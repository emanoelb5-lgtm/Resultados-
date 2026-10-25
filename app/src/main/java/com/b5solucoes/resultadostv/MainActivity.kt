package com.b5solucoes.resultadostv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var repository: Repository
    private lateinit var portraits: Portraits
    private val prefs by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private var elections = emptyList<Election>()
    private var states = emptyList<State>()
    private var statesKey = "ele2026/6258"
    private var loadingStatesKey = ""
    private var pendingLocationKey = ""
    private var selection: Selection? = null
    private var response: JsonResponse? = null
    private var result: ResultData? = null
    private var error = ""
    private var busy = false
    private var active = false
    private var generation = 0
    private var lastRefresh = 0L
    private var page = 0
    private var candidateQuery = ""
    private var auto = true
    private var dialog: Dialog? = null
    private lateinit var root: LinearLayout
    private lateinit var side: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var contentScroll: ScrollView
    private lateinit var heading: TextView
    private lateinit var statusLabel: TextView
    private lateinit var refreshButton: TextView
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
    private val refreshTask = Runnable { if (active && auto) refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        repository = Repository(this)
        portraits = Portraits(this)
        auto = prefs.getBoolean("auto", true)
        val config = JSONObject(resources.openRawResource(R.raw.ele_config).bufferedReader().use { it.readText() })
        elections = DataParser.elections(config)
        states = DataParser.states(JSONObject(resources.openRawResource(R.raw.municipalities_2026).bufferedReader().use { it.readText() }))
        if (BuildConfig.DEBUG && testConfig != null) elections = DataParser.elections(JSONObject(testConfig!!))
        selection = restoreSelection(prefs.getString("selection", null)) ?: run {
            val e = DataParser.defaultElection(elections)
            Selection(e, e.offices("br").first(), Place())
        }
        buildShell()
        renderSide()
        renderContent()
        side.findViewWithTag<View>("election")?.requestFocus()
        if (!(BuildConfig.DEBUG && testResult != null)) updateConfiguration()
    }

    override fun onResume() {
        super.onResume(); active = true
        refresh()
    }
    override fun onPause() {
        active = false; handler.removeCallbacks(refreshTask)
        super.onPause()
    }
    override fun onDestroy() {
        generation++; dialog?.dismiss(); worker.shutdownNow(); portraits.close()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildShell() {
        root = column().apply {
            setBackgroundColor(Palette.background)
            setPadding(dp(28), dp(20), dp(28), dp(14))
            isFocusable = false
        }
        val header = row()
        val brand = text("R", 24f, Palette.background, true).apply {
            gravity = Gravity.CENTER; background = shape(Palette.accent, 10)
        }
        header.addView(brand, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(text("Resultados", 25f, Palette.text, true).apply { setPadding(dp(12), 0, dp(8), 0) })
        header.addView(text("TV", 16f, Palette.accent, true))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        statusLabel = text("", 12f, Palette.muted).apply { gravity = Gravity.END; setPadding(0, 0, dp(14), 0) }
        header.addView(statusLabel)
        refreshButton = action("Atualizar", "refresh") { refresh(manual = true) }
        header.addView(refreshButton)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(48)))
        val body = row().apply { gravity = Gravity.TOP; setPadding(0, dp(18), 0, dp(8)) }
        side = column()
        val sideScroll = ScrollView(this).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false
            addView(side); clipToPadding = false
        }
        body.addView(sideScroll, LinearLayout.LayoutParams(dp(188), -1))
        body.addView(View(this), LinearLayout.LayoutParams(dp(20), 1))
        val main = column()
        heading = text("", 22f, Palette.text, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        main.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        content = column()
        contentScroll = ScrollView(this).apply {
            tag = "results-scroll"
            isFillViewport = false; isVerticalScrollBarEnabled = false
            addView(content); clipToPadding = false
        }
        main.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("Setas: navegar   •   OK: selecionar   •   Voltar: retornar       Fonte: TSE • aplicativo independente", 11f, Palette.muted),
            LinearLayout.LayoutParams(-1, dp(18)))
        setContentView(root)
    }

    private fun renderSide() {
        val focus = currentFocus?.tag as? String
        side.removeAllViews()
        val s = selection ?: return
        side.addView(text("ACOMPANHAR", 11f, Palette.muted, true).apply { setPadding(dp(4), 0, 0, dp(8)) })
        fun item(label: String, tag: String, selected: Boolean = false, click: () -> Unit) {
            side.addView(action(label, tag, selected, click), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        item("Eleição\n${s.election.year} • ${s.election.turn}º turno", "election") { chooseElection() }
        item("Cargo\n${s.office.name}", "office") { chooseOffice() }
        item("Local\n${s.place.name}", "place") { chooseState() }
        item("Zona\n${if (s.place.zone.isBlank()) "Todas" else s.place.zone.toInt()}", "zone") { chooseZone() }
        item(if (isFavorite(s)) "★ Local salvo" else "☆ Salvar consulta", "save", isFavorite(s)) { toggleFavorite() }
        item("Favoritos", "favorites") { chooseFavorite() }
        item("Sobre e ajustes", "about") { showAbout() }
        if (focus != null) side.findViewWithTag<View>(focus)?.requestFocus()
    }

    private fun renderContent() {
        val s = selection ?: return
        val focused = currentFocus
        val focusTag = focused?.tag as? String
        val oldScroll = contentScroll.scrollY
        val focusWasInContent = focused != null && generateSequence(focused.parent) { it.parent }.any { it === content }
        heading.text = "${s.office.name}  /  ${s.place.label}"
        content.removeAllViews()
        statusLabel.text = if (busy) "Consultando TSE…" else "${s.election.year} • ${s.election.turn}º turno"
        refreshButton.text = if (busy) "Consultando…" else "Atualizar"
        val data = result
        if (data == null) {
            val card = column().apply { background = shape(Palette.panel); setPadding(dp(22), dp(22), dp(22), dp(22)) }
            card.addView(text(if (busy) "Buscando os resultados" else "Aguardando consulta", 24f, Palette.text, true))
            card.addView(text(if (error.isBlank()) "Selecione o local e o cargo pelo menu ao lado." else error, 17f, Palette.muted).apply {
                setPadding(0, dp(16), 0, dp(20))
            })
            if (busy) card.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(36), dp(36)))
            else card.addView(action("Tentar novamente", "retry") { refresh(manual = true) })
            content.addView(card, LinearLayout.LayoutParams(-1, -2))
        } else {
            val summary = column().apply {
                background = shape(Palette.panel); setPadding(dp(18), dp(14), dp(18), dp(14))
            }
            val summaryHead = row()
            summaryHead.addView(text("${data.progressText}%", 28f, Palette.accent, true))
            summaryHead.addView(text("  das seções totalizadas", 15f, Palette.text))
            summary.addView(summaryHead)
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 10000; progress = (data.progress * 100).toInt().coerceIn(0, 10000)
                progressTintList = android.content.res.ColorStateList.valueOf(Palette.accent)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(43, 65, 83))
            }
            summary.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(9); bottomMargin = dp(10) })
            summary.addView(text(data.status, 14f, Palette.text, true))
            summary.addView(text("Dados gerados pelo TSE: ${data.generatedAt}", 12f, Palette.muted).apply { setPadding(0, dp(5), 0, 0) })
            content.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            if (response?.cached == true || error.isNotBlank()) {
                content.addView(text((response?.warning?.takeIf { it.isNotBlank() } ?: error) +
                    "\nConsulta salva em: ${formatTime(response?.fetchedAt ?: 0)}", 14f, Palette.warning).apply {
                    setPadding(dp(4), 0, 0, dp(12))
                })
            }
            for (notice in data.notices) content.addView(text(notice, 14f, Palette.warning).apply { setPadding(0, 0, 0, dp(10)) })
            val toolbar = row()
            toolbar.addView(text("CANDIDATURAS", 11f, Palette.muted, true), LinearLayout.LayoutParams(0, -2, 1f))
            toolbar.addView(action(if (candidateQuery.isBlank()) "Buscar candidato" else "Busca: $candidateQuery", "search") { chooseSearch() }.apply { minHeight = dp(38) })
            content.addView(toolbar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            val filtered = data.candidates.filter {
                candidateQuery.isBlank() || searchKey("${it.name} ${it.party} ${it.number}").contains(searchKey(candidateQuery))
            }
            val pageCount = maxOf(1, (filtered.size + 11) / 12)
            page = page.coerceIn(0, pageCount - 1)
            val candidates = filtered.drop(page * 12).take(12)
            if (candidates.isEmpty()) content.addView(text("Nenhuma candidatura para esta busca.", 17f, Palette.muted))
            candidates.chunked(2).forEach { pair ->
                val line = row().apply { gravity = Gravity.TOP }
                pair.forEachIndexed { index, c ->
                    if (index > 0) line.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
                    line.addView(candidateCard(s, c, data.releaseVotes), LinearLayout.LayoutParams(0, dp(190), 1f))
                }
                if (pair.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(12) })
                content.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            if (pageCount > 1) {
                val pagination = row()
                if (page > 0) pagination.addView(action("Página anterior", "previous") { page--; renderContent(); focusContent("search") })
                pagination.addView(text("  ${page + 1} / $pageCount  ", 15f, Palette.muted))
                if (page + 1 < pageCount) pagination.addView(action("Próxima página", "next") { page++; renderContent(); focusContent("search") })
                content.addView(pagination, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            val statistics = column().apply { background = shape(Palette.panel); setPadding(dp(16), dp(14), dp(16), dp(14)) }
            statistics.addView(text("RESUMO DA TOTALIZAÇÃO", 11f, Palette.muted, true))
            val statsText = listOf(
                "Votos válidos: ${formatNumber(data.stat("v", "vv"))}",
                "Brancos: ${formatNumber(data.stat("v", "vb"))}",
                "Nulos: ${formatNumber(data.stat("v", "tvn"))}",
                "Comparecimento: ${formatNumber(data.stat("e", "c"))}",
                "Abstenção: ${formatNumber(data.stat("e", "a"))}",
                "Seções: ${formatNumber(data.stat("s", "st"))} / ${formatNumber(data.stat("s", "ts"))}"
            ).joinToString("\n")
            statistics.addView(text(statsText, 16f, Palette.text).apply { setLineSpacing(dp(4).toFloat(), 1f); setPadding(0, dp(10), 0, 0) })
            statistics.addView(text("Totalização: ${data.totalizedAt}\nConsulta do aplicativo: ${formatTime(response?.fetchedAt ?: 0)}\nPercentuais e situações reproduzidos da fonte. Não são projeções.", 12f, Palette.muted).apply {
                setPadding(0, dp(12), 0, 0)
            })
            content.addView(statistics, LinearLayout.LayoutParams(-1, -2))
        }
        if (focusWasInContent) {
            val restored = focusTag?.let { content.findViewWithTag<View>(it) }
            (restored ?: content.findViewWithTag<View>("search") ?: content.findViewWithTag<View>("retry") ?: refreshButton).requestFocus()
        }
        contentScroll.post { if (!isFinishing) contentScroll.scrollTo(0, oldScroll) }
    }

    private fun candidateCard(s: Selection, c: Candidate, released: Boolean): View {
        val card = column().apply {
            id = View.generateViewId(); tag = "candidate-${c.number}"; isFocusable = true; isClickable = true
            background = focusBackground(); setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { showCandidate(c) }
            contentDescription = "${c.name}, ${c.party}, número ${c.number}, ${if (released) c.percentText + " por cento" else "aguardando divulgação"}"
        }
        val top = row().apply { gravity = Gravity.TOP }
        val portrait = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(Palette.card, 8)
            clipToOutline = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        top.addView(portrait, LinearLayout.LayoutParams(dp(52), dp(62)))
        if (!(BuildConfig.DEBUG && testResult != null)) portraits.load(s, c, portrait)
        val name = column().apply { setPadding(dp(12), 0, 0, 0) }
        name.addView(text(c.name, 18f, Palette.text, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, -2))
        name.addView(text("${c.party}  •  ${c.number}", 13f, Palette.muted).apply { setPadding(0, dp(5), 0, 0) })
        top.addView(name, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(top)
        val percent = if (released) "${c.percentText}%" else "—"
        card.addView(text(percent, 32f, Palette.text, true).apply { setPadding(0, dp(9), 0, 0) })
        card.addView(text(if (released) "${c.votes?.let { numberFormat.format(it) } ?: "—"} votos" else "Aguardando divulgação", 15f, Palette.muted))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 10000; progress = (c.percent * 100).toInt().coerceIn(0, 10000)
            progressTintList = android.content.res.ColorStateList.valueOf(Palette.accent)
        }
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(9) })
        if (c.status.isNotBlank() || c.elected) card.addView(text(c.status.ifBlank { "Eleito" }, 13f, Palette.accent, true).apply { setPadding(0, dp(6), 0, 0) })
        return card
    }

    private fun updateConfiguration() {
        worker.execute {
            runCatching {
                val fresh = repository.get(Endpoints.CONFIG, 3_600_000) {
                    require(it.optString("f") == "o"); require(DataParser.elections(it).isNotEmpty())
                }
                DataParser.elections(fresh.json)
            }.onSuccess { all -> handler.post {
                if (!isDestroyed) {
                    elections = all
                    // Keep the current selection stable while metadata is refreshed.
                    val s = selection
                    val current = all.find { it.id == s?.election?.id && it.cycle == s.election.cycle }
                    if (s != null && current != null) selection = s.copy(election = current)
                }
            } }
        }
    }

    private fun refresh(manual: Boolean = false) {
        handler.removeCallbacks(refreshTask)
        if (!active || busy) { schedule(); return }
        val now = System.currentTimeMillis()
        if (manual && now - lastRefresh < 2_000) { schedule(); return }
        val s = selection ?: return
        if (!s.election.supports(s.place, s.office.code)) {
            result = null; response = null; error = "Este cargo não está disponível no local e turno selecionados. Escolha outro cargo ou local."
            renderContent(); return
        }
        busy = true; lastRefresh = now
        val token = generation
        if (result == null) {
            repository.cached(Endpoints.result(s)) { DataParser.result(it, s) }?.let {
                response = it; result = DataParser.result(it.json, s)
            }
        }
        renderContent()
        worker.execute {
            val outcome = runCatching {
                if (BuildConfig.DEBUG && testResult != null) {
                    val j = JSONObject(testResult!!)
                    JsonResponse(j, testOffline, System.currentTimeMillis(), if (testOffline) "Sem atualização da fonte. Exibindo consulta salva." else "")
                } else repository.get(Endpoints.result(s), if (manual) 0 else 30_000) { DataParser.result(it, s) }
            }
            handler.post {
                if (isDestroyed) return@post
                busy = false
                if (token == generation && selection?.key == s.key) {
                    outcome.onSuccess {
                        runCatching { DataParser.result(it.json, s) }.onSuccess { parsed ->
                            response = it; result = parsed; error = ""
                        }.onFailure { error = it.message ?: "Não foi possível interpretar o resultado." }
                    }.onFailure { error = it.message ?: "Sem conexão com a fonte. Tente novamente." }
                    renderContent(); schedule()
                } else if (active) refresh()
            }
        }
    }
    private fun schedule() {
        handler.removeCallbacks(refreshTask)
        if (active && auto) handler.postDelayed(refreshTask, if (error.isNotBlank() || response?.cached == true) 120_000 else 60_000)
    }

    private fun changeSelection(s: Selection) {
        generation++; selection = s; result = null; response = null; error = ""; page = 0; candidateQuery = ""
        prefs.edit().putString("selection", s.toJson().toString()).apply()
        contentScroll.scrollTo(0, 0); renderSide(); renderContent()
        if (active && !busy) refresh()
    }

    private fun chooseElection() {
        pick("Escolher eleição", elections.map { "${it.label}\n${it.name} • ${it.date}" }) { index ->
            val e = elections[index]
            val s = selection ?: return@pick
            val place = if (e.type == "8") s.place else {
                val preferredUf = s.place.uf.takeIf { it != "br" && it != "zz" } ?: "pa"
                val uf = e.coverage.firstOrNull { it.uf != "br" }?.uf ?: preferredUf
                val state = states.find { it.code == uf }
                val mu = e.coverage.find { it.uf == uf }?.municipalities?.firstOrNull()
                if (mu != null) Place(uf, mu, state?.municipalities?.find { it.code == mu }?.name ?: mu)
                else Place(uf, name = state?.name ?: uf.uppercase(Locale.ROOT))
            }
            val office = e.offices(place.uf).firstOrNull() ?: return@pick
            changeSelection(Selection(e, office, place))
            loadStates(e, office.code in setOf("11", "13", "25") && place.municipality.isBlank())
        }
    }
    private fun chooseOffice() {
        val s = selection ?: return
        val offices = s.election.offices(s.place.uf)
        if (offices.isEmpty()) { message("Nenhum cargo disponível", "Escolha uma eleição e um local compatíveis."); return }
        pick("Escolher cargo", offices.map { it.name }) { changeSelection(s.copy(office = offices[it])) }
    }
    private fun chooseState() {
        val s = selection ?: return
        if (statesKey != "${s.election.cycle}/${s.election.id}") {
            Toast.makeText(this, "Carregando os locais desta eleição…", Toast.LENGTH_SHORT).show()
            loadStates(s.election, true)
            return
        }
        val available = states.filter { state -> s.election.offices(state.code).isNotEmpty() }
        val country = if (s.election.offices("br").isNotEmpty()) listOf(State("br", "Brasil • resultado nacional", emptyList())) else emptyList()
        val options = country + available
        if (options.isEmpty()) { message("Locais indisponíveis", "Aguarde o carregamento da lista de municípios."); loadStates(s.election); return }
        pick("Estado ou Brasil", options.map { it.name }) { index ->
            val state = options[index]
            if (state.code == "br") changeSelection(s.copy(place = Place())) else chooseMunicipality(state)
        }
    }
    private fun chooseMunicipality(state: State) {
        val s = selection ?: return
        val restrictions = s.election.coverage.filter { it.uf == state.code }.flatMap { it.municipalities }.toSet()
        val cities = state.municipalities.filter { restrictions.isEmpty() || it.code in restrictions }
        val municipalOnly = s.office.code in setOf("11", "13", "25") || restrictions.isNotEmpty()
        val labels = (if (municipalOnly) emptyList() else listOf("Todo o estado • ${state.name}")) + cities.map { it.name }
        pick("Local em ${state.name}", labels) { i ->
            val offset = if (municipalOnly) 0 else 1
            val place = if (i < offset) Place(state.code, name = state.name) else {
                val m = cities[i - offset]; Place(state.code, m.code, m.name)
            }
            val o = s.election.offices(place.uf).find { it.code == s.office.code } ?: s.election.offices(place.uf).first()
            changeSelection(s.copy(place = place, office = o))
        }
    }
    private fun chooseZone() {
        val s = selection ?: return
        if (s.place.municipality.isBlank()) { message("Escolha um município", "Primeiro selecione um município no menu Local para consultar por zona eleitoral."); return }
        val municipality = states.find { it.code == s.place.uf }?.municipalities?.find { it.code == s.place.municipality }
        val zones = municipality?.zones ?: emptyList()
        pick("Zona eleitoral", listOf("Todas as zonas do município") + zones.map { "Zona ${it.toInt()}" }) {
            changeSelection(s.copy(place = s.place.copy(zone = if (it == 0) "" else zones[it - 1])))
        }
    }
    private fun loadStates(e: Election, openAfter: Boolean = false) {
        val key = "${e.cycle}/${e.id}"
        if (openAfter) pendingLocationKey = key
        if (statesKey == key) {
            if (pendingLocationKey == key) { pendingLocationKey = ""; chooseState() }
            return
        }
        if (loadingStatesKey == key) return
        loadingStatesKey = key
        worker.execute {
            val outcome = runCatching { DataParser.states(repository.get(Endpoints.municipalities(e), 86_400_000) {
                require(it.optString("f") == "o"); require(DataParser.states(it).isNotEmpty())
            }.json) }
            handler.post {
                if (isDestroyed) return@post
                if (loadingStatesKey == key) loadingStatesKey = ""
                outcome.onSuccess { fresh ->
                    if (selection?.election?.id == e.id && selection?.election?.cycle == e.cycle) {
                        states = fresh; statesKey = key
                        if (pendingLocationKey == key) { pendingLocationKey = ""; chooseState() }
                    }
                }.onFailure {
                    if (pendingLocationKey == key) {
                        pendingLocationKey = ""
                        message("Locais indisponíveis", "Não foi possível carregar a lista desta eleição. Verifique a conexão e tente novamente.")
                    }
                }
            }
        }
    }

    private fun favorites(): List<JSONObject> = runCatching { JSONArray(prefs.getString("favorites", "[]")).objects() }.getOrDefault(emptyList())
    private fun restoreSelection(value: String?): Selection? = runCatching {
        if (value == null) return@runCatching null
        val j = JSONObject(value)
        val e = elections.first { it.id == j.getString("election") && it.cycle == j.getString("cycle") }
        val place = Place.fromJson(j.getJSONObject("place"))
        val office = e.offices(place.uf).first { it.code == j.getString("office") }
        Selection(e, office, place)
    }.getOrNull()
    private fun isFavorite(s: Selection) = favorites().any { restoreSelection(it.toString())?.key == s.key }
    private fun toggleFavorite() {
        val s = selection ?: return
        val current = favorites().toMutableList()
        if (isFavorite(s)) current.removeAll { restoreSelection(it.toString())?.key == s.key }
        else { if (current.size >= 20) current.removeAt(0); current.add(s.toJson()) }
        prefs.edit().putString("favorites", JSONArray(current).toString()).apply()
        renderSide()
        Toast.makeText(this, if (isFavorite(s)) "Consulta salva nos favoritos" else "Consulta removida dos favoritos", Toast.LENGTH_SHORT).show()
    }
    private fun chooseFavorite() {
        val list = favorites().mapNotNull { restoreSelection(it.toString()) }
        if (list.isEmpty()) { message("Seus favoritos", "Selecione o local e o cargo e use Salvar consulta para acompanhar com poucos cliques."); return }
        pick("Consultas favoritas", list.map { "${it.title}\n${it.election.year} • ${it.election.turn}º turno" }) { changeSelection(list[it]); loadStates(list[it].election) }
    }
    private fun chooseSearch() {
        textInput("Buscar candidatura", candidateQuery, "Nome, partido ou número") { value ->
            candidateQuery = value; page = 0; renderContent(); focusContent("search")
        }
    }
    private fun showCandidate(c: Candidate) {
        val s = selection ?: return
        val r = result ?: return
        val votes = if (r.releaseVotes) "${c.votes?.let { numberFormat.format(it) } ?: "—"} votos • ${c.percentText}%" else "Aguardando divulgação de votos pelo TSE"
        val details = listOf(c.fullName, "${c.party} • Número ${c.number}", votes,
            c.status.takeIf { it.isNotBlank() }?.let { "Situação: $it" } ?: "Situação eleitoral ainda não informada",
            c.destination.takeIf { it.isNotBlank() }?.let { "Destinação dos votos: $it" } ?: "",
            c.companions.joinToString("\n"), "${s.title}\n${s.election.year} • ${s.election.turn}º turno",
            "Dados gerados pelo TSE: ${r.generatedAt}").filter { it.isNotBlank() }.joinToString("\n\n")
        message(c.name, details)
    }
    private fun showAbout() {
        pick("Sobre e ajustes", listOf(
            "Atualização automática: ${if (auto) "ativada (60 segundos)" else "desativada"}",
            "Fonte dos resultados e privacidade", "Como usar o controle remoto")) {
            when (it) {
                0 -> { auto = !auto; prefs.edit().putBoolean("auto", auto).apply(); schedule() }
                1 -> message("Resultados TV • ${BuildConfig.VERSION_NAME}",
                    "Aplicativo independente de Emanuel Bonfim / B5 Soluções. Não é um aplicativo oficial e não possui vínculo com o Tribunal Superior Eleitoral.\n\n" +
                    "Fonte: Tribunal Superior Eleitoral — https://resultados.tse.jus.br\n\nConsulta os arquivos públicos do ambiente oficial. Percentuais, votos e situações eleitorais são reproduzidos da fonte, com data e hora. A aplicação não calcula projeções nem declara vencedores.\n\n" +
                    "Sem cadastro, anúncios, localização ou ferramentas de rastreamento. Favoritos, preferências e consultas recentes ficam neste aparelho. Ao consultar resultados e fotos, o TSE recebe os dados técnicos usuais da conexão, como o endereço IP.\n\n" +
                    "Código: https://github.com/emanoelb5-lgtm/Resultados- • Licença MIT.")
                else -> message("Controle remoto", "Use as setas para mover o destaque, OK para abrir uma opção e Voltar para fechar as janelas.\n\nNo menu Local, escolha o estado e depois o município. A consulta nacional está disponível para Presidente.\n\nBusque pelo nome ou número nos cargos com muitos candidatos. Use Favoritos para voltar às consultas salvas.\n\nSem internet, o aplicativo mantém a última consulta disponível para a mesma seleção e mostra a data em que foi salva.")
            }
        }
    }

    private fun pick(title: String, labels: List<String>, onPick: (Int) -> Unit) {
        dialog?.dismiss()
        val d = Dialog(this)
        dialog = d
        val panel = column().apply {
            background = shape(Palette.background); setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        val titleRow = row()
        titleRow.addView(text(title, 23f, Palette.text, true), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(action("Fechar", "dialog-close") { d.dismiss() })
        panel.addView(titleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val search = if (labels.size > 12) EditText(this).apply {
            hint = "Buscar nesta lista"; setTextColor(Palette.text); setHintTextColor(Palette.muted)
            textSize = 18f; isSingleLine = true; contentDescription = "Buscar nesta lista"
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = focusBackground()
            panel.addView(this, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })
        } else null
        val list = ListView(this).apply {
            divider = null; dividerHeight = dp(5); isFocusable = true; isFocusableInTouchMode = true
            selector = focusBackground(); descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setPadding(dp(3), dp(3), dp(3), dp(3)); clipToPadding = false
        }
        var indexes = labels.indices.toList()
        fun fill(query: String) {
            indexes = labels.indices.filter { searchKey(labels[it]).contains(searchKey(query)) }
            list.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, indexes.map { labels[it] }) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getView(position, convertView, parent) as TextView).apply {
                        textSize = 17f; setTextColor(Palette.text); minHeight = dp(56)
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                        isFocusable = false; maxLines = 4
                    }
            }
        }
        fill("")
        search?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { fill(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })
        list.setOnItemClickListener { _, _, position, _ ->
            val index = indexes.getOrNull(position) ?: return@setOnItemClickListener
            d.dismiss(); onPick(index)
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        d.setContentView(panel)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * .78).toInt(), (resources.displayMetrics.heightPixels * .86).toInt())
        list.requestFocus(); list.setSelection(0)
        d.setOnDismissListener { if (dialog === d) dialog = null }
    }

    private fun textInput(title: String, value: String, hintText: String, onValue: (String) -> Unit) {
        val input = EditText(this).apply { setText(value); hint = hintText; isSingleLine = true; setTextColor(Palette.text) }
        val box = column().apply { setPadding(dp(22), dp(16), dp(22), dp(16)); addView(input) }
        dialog = AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("Buscar") { _, _ -> onValue(input.text.toString().trim()) }
            .setNeutralButton("Limpar busca") { _, _ -> onValue("") }
            .setNegativeButton("Cancelar", null).create().also { it.show() }
    }
    private fun message(title: String, value: String) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(value).setPositiveButton("Fechar", null).create().also {
            it.show(); it.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
        }
    }
    private fun focusContent(tag: String) { content.findViewWithTag<View>(tag)?.requestFocus() }
    private fun formatNumber(value: String) = value.toLongOrNull()?.let { numberFormat.format(it) } ?: value
    private fun formatTime(timestamp: Long): String = if (timestamp == 0L) "—" else
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            contentScroll.smoothScrollBy(0, dp(if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) 230 else -230)); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    @Deprecated("Legacy TV back handling")
    override fun onBackPressed() {
        if (dialog?.isShowing == true) { dialog?.dismiss(); return }
        if (contentScroll.scrollY > dp(40)) { contentScroll.smoothScrollTo(0, 0); side.findViewWithTag<View>("election")?.requestFocus(); return }
        super.onBackPressed()
    }
    companion object {
        // Test data is accepted only by debug builds; R8 removes these branches from release.
        @Volatile var testResult: String? = null
        @Volatile var testConfig: String? = null
        @Volatile var testOffline = false
    }
}
