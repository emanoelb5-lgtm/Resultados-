package com.b5solucoes.resultadostv

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataParserTest {
    private val election = Election("6258", "ele2026", "Federal 2026", "25/10/2026", "2", "8",
        listOf(Coverage("br", emptySet(), listOf(Office("1", "Presidente", "1")))))
    private val selection = Selection(election, Office("1", "Presidente", "1"), Place())
    private fun document(): JSONObject = JSONObject("""{
      "ele":"6258","t":"2","f":"o","tpabr":"br","cdabr":"br","dv":"s",
      "and":"p","tf":"n","dg":"25/10/2026","hg":"18:10:12","dt":"25/10/2026","ht":"18:10:00",
      "s":{"pst":"50,00","pstn":"50"},"carg":[{"cd":"1","agr":[{"par":[
       {"sg":"TESTE","cand":[
        {"n":"98","nm":"Candidata Teste A","nmu":"TESTE A","seq":"2","vap":"4321000000","pvap":"54,20","pvapn":"54,203456","e":"n","st":""},
        {"n":"99","nm":"Candidato Teste B","nmu":"TESTE B","seq":"1","vap":"3651000000","pvap":"45,80","pvapn":"45,796544","e":"n","st":""}
       ]}]}]}],"v":{"vv":"7972000000"}
    }""")
    @Test fun reproducesOfficialPercentAndDoesNotDeclareWinnerInPartialCount() {
        val r = DataParser.result(document(), selection)
        assertEquals("TESTE A", r.candidates.first().name)
        assertEquals(4321000000L, r.candidates.first().votes)
        assertEquals("54,20", r.candidates.first().percentText)
        assertTrue(r.candidates.none { it.elected })
        assertEquals("Apuração parcial", r.status)
    }
    @Test fun hidesVotesAndElectionStatusWhenDisclosureIsNotAuthorized() {
        val d = document().put("dv", "n")
        val candidate = d.getJSONArray("carg").getJSONObject(0).getJSONArray("agr").getJSONObject(0)
            .getJSONArray("par").getJSONObject(0).getJSONArray("cand").getJSONObject(0)
        candidate.put("e", "s").put("st", "Eleito")
        val r = DataParser.result(d, selection)
        assertFalse(r.releaseVotes)
        assertTrue(r.candidates.all { it.votes == null && it.percentText == "—" && !it.elected && it.status.isBlank() })
        assertEquals("—", r.stat("v", "vv"))
    }
    @Test fun rejectsSimulationOrMismatchedSelection() {
        listOf(document().put("f", "s"), document().put("t", "1"), document().put("ele", "6257"),
            document().put("cdabr", "pa"), document().put("tpabr", "uf")).forEach {
            assertThrows(IllegalArgumentException::class.java) { DataParser.result(it, selection) }
        }
    }
    @Test fun totalizedSectionsDoNotImplyFinalResult() {
        val d = document().put("and", "f").put("tf", "n")
        val r = DataParser.result(d, selection)
        assertEquals("Totalização concluída nesta abrangência", r.status)
        assertTrue(r.candidates.none { it.elected })
    }
    @Test fun buildsZeroPaddedMunicipalityAndZoneUrls() {
        val s = selection.copy(place = Place("pa", "4120", "Município teste", "6"))
        assertEquals("https://resultados.tse.jus.br/oficial/ele2026/6258/dados/pa/pa04120-z0006-c0001-e006258-u.json", Endpoints.result(s))
        assertThrows(IllegalArgumentException::class.java) { Endpoints.result(s.copy(place = Place("../"))) }
    }
    @Test fun secondRoundGovernorOnlyAppearsForParticipatingStatesAndDfUsesDistrictDeputy() {
        val e = election.copy(type = "1", coverage = listOf(Coverage("am", emptySet(), listOf(Office("3", "Governador", "1")))))
        assertTrue(e.supports(Place("am"), "3"))
        assertFalse(e.supports(Place("pa"), "3"))
        val firstRound = e.copy(coverage = listOf(Coverage("br", emptySet(), listOf(
            Office("7", "Estadual", "2"), Office("8", "Distrital", "2")))))
        assertEquals(listOf("8"), firstRound.offices("df").map { it.code })
        assertEquals(listOf("7"), firstRound.offices("pa").map { it.code })
    }
    @Test fun correctlyRestoresZoneCodesAndSearchIgnoresAccents() {
        val place = Place("pa", "04120", "Local", "0006")
        assertEquals(place, Place.fromJson(place.toJson()))
        assertEquals("garrafao", searchKey("GARRAFÃO"))
        assertEquals("04120", pad("4120", 5))
    }
    @Test fun municipalOfficeRequiresMunicipalityAndZoneRequiresMunicipality() {
        val e = election.copy(coverage = listOf(Coverage("pa", setOf("05878"), listOf(Office("11", "Prefeito", "1")))))
        assertFalse(e.supports(Place("pa"), "11"))
        assertFalse(e.supports(Place("pa", "04120"), "11"))
        assertTrue(e.supports(Place("pa", "05878"), "11"))
        assertFalse(election.supports(Place("pa", zone = "0081"), "1"))
    }
}
