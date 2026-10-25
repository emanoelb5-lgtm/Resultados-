# Resultados TV

Resultados eleitorais na televisão, com interface nativa em Kotlin e navegação pelo controle remoto.

**[Baixar Resultados-TV-1.0.0.apk](https://github.com/emanoelb5-lgtm/Resultados-/releases/latest/download/Resultados-TV-1.0.0.apk)** · [Versões e verificações](https://github.com/emanoelb5-lgtm/Resultados-/releases) · [Compilação e testes](https://github.com/emanoelb5-lgtm/Resultados-/actions)

Aplicativo independente de Emanuel Bonfim / B5 Soluções. **Não é um produto oficial do TSE e não possui vínculo com a Justiça Eleitoral.** Os resultados são consultados diretamente nos arquivos públicos do [Tribunal Superior Eleitoral](https://resultados.tse.jus.br), com indicação da data e da hora da fonte.

## Recursos

- Android TV, Google TV e TV boxes com Android 6.0 ou superior; APK universal, sem biblioteca nativa específica de processador.
- Tela horizontal, cartões de candidaturas, percentuais grandes e destaque visível no foco.
- Navegação por setas, OK e Voltar; botões de canal ajudam a rolar os resultados.
- Eleições e turnos publicados pelo TSE; cargo, estado, município e zona eleitoral.
- Resultado nacional para Presidente e disponibilidade de cargos conforme o local e turno.
- Votos, percentuais, situação eleitoral, vice/suplentes e resumo da totalização.
- Busca por nome, partido ou número e paginação para cargos com muitos candidatos.
- Até 20 consultas favoritas salvas no aparelho.
- Atualização a cada 60 segundos, com opção de desativar, cache e espera maior após falhas da fonte.
- Última consulta disponível sem conexão, identificada com data e aviso.
- Sem cadastro e sem anúncios; única permissão: internet.

## Instalar na TV

1. Baixe o APK pelo link acima e envie para a televisão, por exemplo usando um pendrive ou um aplicativo de transferência de arquivos.
2. No Android, permita a instalação pelo gerenciador de arquivos utilizado e abra o APK.
3. Abra **Resultados TV** na lista de aplicativos da televisão.
4. Use **Eleição**, **Cargo** e **Local** para definir a consulta; no estado, escolha o total estadual ou um município.

O aplicativo pode ser instalado junto ao Resultados oficial, pois usa pacote e assinatura próprios. Atualizações deste projeto devem manter a assinatura preservada para atualizar a instalação existente.

## Sobre os dados

O aplicativo reproduz votos e percentuais dos arquivos oficiais EA20. Não calcula projeções e não deduz eleitos pela posição ou pelo percentual de votos. A situação eleitoral exibida é a informada na fonte. Quando o arquivo não autoriza a divulgação de votos, essas informações permanecem ocultas.

A disponibilidade e a atualização dos resultados dependem do TSE e da conexão do aparelho. A informação salva corresponde à consulta indicada na tela; ela não é apresentada como resultado atualizado. Fotos são opcionais: uma falha no retrato não impede a leitura dos votos.

## Desenvolvimento e assinatura

[Orientações de desenvolvimento](docs/DESENVOLVIMENTO.md) · [Privacidade](PRIVACY.md) · [Licença MIT](LICENSE)

A compilação executa testes unitários, lint e testes de navegação em emulador Android TV 720p. As capturas automáticas de interface usam candidaturas fictícias, sem relação com resultados reais. O APK é assinado fora do GitHub com chave privada preservada; somente o certificado público fica em [signing/](signing/).

O aplicativo oficial enviado como referência não é incluído no repositório nem redistribuído. Este projeto contém código e identidade visual próprios, consumindo os dados públicos documentados pelo TSE.
