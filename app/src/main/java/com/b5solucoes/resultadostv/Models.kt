package com.b5solucoes.resultadostv

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun number(text: String): Double = text.replace(",", ".").toDoubleOrNull() ?: 0.0
internal fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
internal fun pad(value: String, width: Int): String {
    require(value.matches(Regex("[0-9]{1,$width}"))) { "Código inválido" }
    return value.padStart(width, '0')
}

data class Office(val code: String, val name: String, val type: String)
data class Coverage(val uf: String, val municipalities: Set<String>, val offices: List<Office>)
data class Election(val id: String, val cycle: String, val name: String, val date: String,
                    val turn: String, val type: String, val coverage: List<Coverage>) {
    val year: String get() = cycle.removePrefix("ele")
    val label: String get() = "$year • ${turn}º turno • " + when (type) {
        "8" -> "Presidente"; "1" -> "Cargos estaduais"; "2", "3" -> "Municipal"; else -> name
    }
    val time: Long get() = runCatching {
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }.parse(date)?.time ?: 0
    }.getOrDefault(0)
    fun offices(uf: String): List<Office> = coverage.filter { it.uf == "br" || it.uf == uf }
        .flatMap { it.offices }.distinctBy { it.code }.filter {
            (it.code != "8" || uf == "df") && (it.code != "7" || uf != "df") &&
                (uf != "br" || it.code == "1") && (uf != "zz" || it.code == "1")
        }
    fun supports(place: Place, office: String): Boolean = coverage.any {
        (it.uf == "br" || it.uf == place.uf) && it.offices.any { o -> o.code == office } &&
            (it.municipalities.isEmpty() || place.municipality in it.municipalities)
    } && offices(place.uf).any { it.code == office }
}

data class Place(val uf: String = "br", val municipality: String = "", val name: String = "Brasil",
                 val zone: String = "") {
    fun toJson() = JSONObject().put("uf", uf).put("municipality", municipality).put("name", name).put("zone", zone)
    val label: String get() = if (zone.isBlank()) name else "$name • Zona ${zone.toInt()}"
    companion object {
        fun fromJson(j: JSONObject) = Place(j.optString("uf", "br"), j.optString("municipality"),
            j.optString("name", "Brasil"), j.optString("zone"))
    }
}
data class Municipality(val code: String, val name: String, val zones: List<String>)
data class State(val code: String, val name: String, val municipalities: List<Municipality>)
data class Selection(val election: Election, val office: Office, val place: Place) {
    val key get() = "${election.cycle}/${election.id}/${office.code}/${place.uf}/${place.municipality}/${place.zone}"
    val title get() = "${office.name} • ${place.label}"
    fun toJson() = JSONObject().put("election", election.id).put("cycle", election.cycle)
        .put("office", office.code).put("place", place.toJson())
}

data class Candidate(val number: String, val name: String, val fullName: String,
    val party: String, val votes: Long?, val percentText: String, val percent: Double,
    val destination: String, val status: String, val elected: Boolean,
    val sequence: Int, val photoId: String, val companions: List<String>)
data class ResultData(val raw: JSONObject, val candidates: List<Candidate>, val releaseVotes: Boolean,
    val generatedAt: String, val totalizedAt: String, val progressText: String, val progress: Double,
    val status: String, val notices: List<String>) {
    fun stat(group: String, key: String) = if (releaseVotes) raw.optJSONObject(group)?.optString(key, "—") ?: "—" else "—"
}

object DataParser {
    fun elections(j: JSONObject): List<Election> = j.optJSONArray("pl").objects().flatMap { p ->
        p.optJSONArray("e").objects().mapNotNull { e ->
            val cycle = p.optString("c")
            if (!cycle.matches(Regex("ele[0-9]{4}"))) return@mapNotNull null
            val coverage = e.optJSONArray("abr").objects().map { a ->
                Coverage(a.getString("cd"), a.optJSONArray("mu").objects().map { it.getString("cd") }.toSet(),
                    a.optJSONArray("cp").objects().map { Office(it.getString("cd"), it.getString("ds"), it.optString("tp")) })
            }
            Election(e.getString("cd"), cycle, e.getString("nm"), p.getString("dt"), e.getString("t"), e.optString("tp"), coverage)
        }
    }.distinctBy { "${it.cycle}/${it.id}" }.sortedWith(compareByDescending<Election> { it.time }.thenBy { it.name })

    fun defaultElection(all: List<Election>, now: Long = Date().time): Election =
        all.firstOrNull { it.type == "8" && it.time <= now } ?: all.first { it.type == "8" }

    fun states(j: JSONObject): List<State> = j.optJSONArray("abr").objects().map { a ->
        State(a.getString("cd"), a.getString("ds"), a.optJSONArray("mu").objects().map { m ->
            val zones = m.optJSONArray("z")
            Municipality(pad(m.getString("cd"), 5), m.getString("nm"),
                if (zones == null) emptyList() else (0 until zones.length()).map { pad(zones.getString(it), 4) })
        }.sortedBy { searchKey(it.name) })
    }.sortedBy { searchKey(it.name) }

    fun result(j: JSONObject, selection: Selection): ResultData {
        require(j.optString("f") == "o") { "O servidor não retornou dados do ambiente oficial." }
        require(j.optString("ele") == selection.election.id && j.optString("t") == selection.election.turn) {
            "O arquivo recebido pertence a outra eleição ou turno."
        }
        val expectedType = when {
            selection.place.zone.isNotEmpty() -> "zona"
            selection.place.municipality.isNotEmpty() -> "mu"
            selection.place.uf == "br" -> "br"
            else -> "uf"
        }
        val expectedCode = when(expectedType) {
            "zona" -> selection.place.zone
            "mu" -> selection.place.municipality
            else -> selection.place.uf
        }
        require(j.optString("tpabr") == expectedType && j.optString("cdabr") == expectedCode) {
            "A abrangência recebida não corresponde ao local selecionado."
        }
        val show = j.optString("dv") == "s"
        val cargo = j.optJSONArray("carg").objects().find { it.optString("cd") == selection.office.code }
        val question = j.optJSONArray("perg").objects().find { it.optString("cd") == selection.office.code }
        require(cargo != null || question != null) { "O cargo não consta no arquivo recebido." }
        val candidates = mutableListOf<Candidate>()
        fun add(c: JSONObject, party: String) {
            val vs = c.optJSONArray("vs").objects().map {
                val role = when (it.optString("tp")) { "v" -> "Vice"; "s1" -> "1º suplente"; "s2" -> "2º suplente"; else -> "Integrante" }
                "$role: ${it.optString("nmu", it.optString("nm"))} (${it.optString("sgp")})"
            }
            candidates.add(Candidate(c.getString("n"), c.optString("nmu", c.optString("ds", c.optString("nm"))),
                c.optString("nm", c.optString("ds")), party,
                if (show) c.optString("vap").toLongOrNull() else null,
                if (show) c.optString("pvap", "—") else "—",
                if (show) number(c.optString("pvapn", c.optString("pvap"))) else 0.0,
                c.optString("dvt"), if (show) c.optString("st") else "", show && c.optString("e") == "s",
                c.optInt("seq", Int.MAX_VALUE), c.optString("sqcand"), vs))
        }
        cargo?.optJSONArray("agr").objects().forEach { a -> a.optJSONArray("par").objects().forEach { p ->
            p.optJSONArray("cand").objects().forEach { add(it, p.optString("sg")) }
        } }
        question?.optJSONArray("resp").objects().forEach { add(it, "Consulta popular") }
        val ordered = if (show) candidates.sortedWith(compareByDescending<Candidate> { it.votes ?: 0 }
            .thenBy { it.sequence }.thenBy { it.number }) else candidates.sortedBy { it.number }
        val sections = j.optJSONObject("s")
        val notices = mutableListOf<String>()
        if (j.optString("mntf").isNotBlank()) notices.add(j.optString("mntf"))
        if (j.optString("esae") == "s") {
            notices.add("Sem atribuição de eleitos pelo TSE.")
            val reasons = j.optJSONArray("mnae")
            if (reasons != null) (0 until reasons.length()).forEach { notices.add(reasons.optString(it)) }
        }
        val status = when {
            !show -> "Aguardando divulgação de votos"
            j.optString("tf") == "s" -> "Totalização final informada pelo TSE"
            j.optString("and") == "f" -> "Totalização concluída nesta abrangência"
            j.optString("and") == "n" -> "Apuração não iniciada"
            else -> "Apuração parcial"
        }
        return ResultData(j, ordered, show, "${j.optString("dg")} ${j.optString("hg")}",
            "${j.optString("dt")} ${j.optString("ht")}",
            if (show) sections?.optString("pst", "—") ?: "—" else "—",
            if (show) sections?.let { number(it.optString("pstn", it.optString("pst"))) } ?: 0.0 else 0.0,
            status, notices)
    }
}

object Endpoints {
    const val BASE = "https://resultados.tse.jus.br/oficial/"
    const val CONFIG = "${BASE}comum/config/ele-c.json"
    fun municipalities(e: Election) = "$BASE${e.cycle}/${pad(e.id, 6).toInt()}/config/mun-e${pad(e.id, 6)}-cm.json"
    fun result(s: Selection): String {
        require(s.election.cycle.matches(Regex("ele[0-9]{4}")))
        require(s.place.uf.matches(Regex("[a-z]{2}")))
        val mu = if (s.place.municipality.isBlank()) "" else pad(s.place.municipality, 5)
        val zone = if (s.place.zone.isBlank()) "" else "-z${pad(s.place.zone, 4)}"
        return "$BASE${s.election.cycle}/${pad(s.election.id, 6).toInt()}/dados/${s.place.uf}/${s.place.uf}$mu$zone-c${pad(s.office.code, 4)}-e${pad(s.election.id, 6)}-u.json"
    }
}
