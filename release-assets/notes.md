Resultados TV 1.0.0 — primeira versão independente para Android TV e Google TV.

- Interface horizontal nativa em Kotlin, com navegação por setas, OK e Voltar.
- Consulta aos dados públicos do TSE, com eleição, turno, cargo, estado, município e zona.
- Cartões de candidaturas, votos, percentuais, situação eleitoral e vice/suplentes.
- Favoritos, busca e atualização automática a cada 60 segundos.
- Última consulta salva identificada quando a fonte ou a internet estiver indisponível.
- Sem cadastro, anúncios ou rastreamento. Android 6.0 ou superior.

Instale `Resultados-TV-1.0.0.apk` na televisão. O aplicativo aparecerá como **Resultados TV** na lista de apps.

Este é um projeto independente, sem vínculo com o Tribunal Superior Eleitoral. O aplicativo oficial não é redistribuído. Os dados da fonte são apresentados com data e hora; a aplicação não calcula projeções nem deduz vencedores.

Validação: testes do parser, lint, navegação por controle remoto em emulador Android TV 720p e instalação/abertura da versão de produção assinada. As capturas automatizadas de testes usam candidaturas fictícias.

A assinatura privada foi preservada para futuras atualizações. `SHA256SUMS.txt` permite conferir a integridade do APK; `release.json` identifica o certificado, o commit e a execução de compilação e testes.
