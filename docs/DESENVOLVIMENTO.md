# Desenvolvimento

Projeto nativo em Kotlin, com Views do Android, sem WebView e sem dependências de interface ou rede em produção além da biblioteca padrão Kotlin. Requer JDK 17, Android SDK 35 e Gradle 8.10.2.

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
gradle connectedDebugAndroidTest
```

O CI usa um emulador Android TV API 28, 1280 × 720. As capturas de QA usam candidaturas explicitamente fictícias. As entradas de teste são aceitas exclusivamente no build de depuração e os ramos são removidos por R8 na versão de produção.

As configurações de eleições e municípios de 2026 embarcadas são arquivos públicos obtidos do TSE em 25/10/2026, para permitir abrir o seletor antes da primeira conexão. Nenhum resultado de votação é embarcado no APK. A configuração de eleições é atualizada pela fonte; listagens de municípios são consultadas ao trocar de eleição.

Fontes técnicas:

- https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados
- https://resultados.tse.jus.br/oficial/comum/config/ele-c.json
- https://developer.android.com/training/tv/get-started/create
- https://developer.android.com/training/tv/get-started/navigation

O parser lê o esquema EA20, preserva os campos `pvap`, `vap`, `st`, `e`, `dv`, `tf`, `and`, `esae`, `mntf` e as informações de vice/suplentes. Não calcula percentuais nem projeta vencedores. Se `dv` for diferente de `s`, suprime votos, percentuais e indicação de eleição. Exige ambiente oficial e correspondência entre eleição, turno, cargo e abrangência antes de salvar a consulta.

As consultas de dados são seriadas, com intervalo mínimo de 500 ms, ETag/Last-Modified e bloqueio temporário após erros 404/403/429. Atualização normal: 60 segundos; falhas: 120 segundos, respeitando ainda os bloqueios locais por URL e por host. Retratos são opcionais, têm cache em memória/disco, resolução limitada e cache negativo para fotos ausentes.

O pacote usa namespace próprio (`com.b5solucoes.resultadostv`) e pode coexistir com o aplicativo oficial. O APK original não é distribuído nem necessário para compilar. A licença MIT cobre apenas este código original e a identidade visual criada para este projeto; os dados e imagens públicos continuam atribuídos à fonte.

A chave privada é preservada fora do GitHub. O repositório guarda somente o certificado público e seu SHA-256. Para publicar, o APK é compilado e testado no CI, assinado com a chave privada e enviado a uma branch `release/…`. A publicação valida o certificado, a identidade do pacote, o sucesso do CI e a igualdade das entradas do APK com o build de origem.
