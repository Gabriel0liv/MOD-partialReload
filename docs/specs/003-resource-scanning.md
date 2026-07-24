# Spec 003 — Scanning de recursos

## 1. Contexto

O `ResourceManager` oferece a visão server-data dos packs já selecionados.

## 2. Problema

É necessário observar recursos e origem sem executar conteúdo nem alterar managers.

## 3. Objetivos

Enumerar recursos visíveis, classificar paths, registrar pack de origem e SHA-256.

## 4. Não objetivos

Parse semântico completo, leitura de assets, filesystem watcher ou reload de packs.

## 5. Terminologia

Path lógico é o path do `ResourceLocation`; pack de origem é `Resource.sourcePackId()` quando disponível; fingerprint contém algoritmo/hash/tamanho.

## 6. Requisitos funcionais

- RF-003-1: descobrir namespaces/paths via `ResourceManager`, enumerando roots server-data válidos e explicitamente suportados pela fase.
- RF-003-2: mapear exatamente os diretórios definidos na fase 1.
- RF-003-3: classificar desconhecidos como UNKNOWN.
- RF-003-4: SHA-256 deve cobrir bytes completos do recurso vencedor.
- RF-003-5: impor `max_scanned_resources` e timeout cooperativo.
- RF-003-6: ignorar PackType.CLIENT_RESOURCES por operar somente na visão SERVER_DATA.

## 7. Requisitos não funcionais

Scan é read-only, fecha streams, tem ordem determinística e não faz IO pesado na server thread: comando agenda preparação no executor de background e publica resultado na server thread.

## 8. Invariantes

Não chama reload, não executa arquivos, não modifica `ResourceManager`, não descarta UNKNOWN silenciosamente dentro dos roots observados. O prefixo vazio não é usado porque `PackResources` 1.20.1 o rejeita como path inválido.

## 9. Modelo de erros

Limite, timeout, IO e hash são `PartialReloadException` com operação e recurso quando conhecido. Snapshot parcial não substitui o último snapshot válido.

## 10. Riscos

Pack mudar durante leitura; `sourcePackId` não ser caminho; múltiplas camadas ocultas; loaders futuros fora dos roots conhecidos exigirem que o provider acrescente sua raiz de descoberta. Fase 1 observa apenas recurso vencedor.

## 11. Critérios de aceitação

Todos os mappings solicitados passam em teste, hashes são reproduzíveis, UNKNOWN dentro de roots observados aparece e assets não entram.

## 12. Cenários de teste

Cada diretório/mapeamento; extensão errada; path desconhecido; bytes iguais/diferentes; limite excedido.

## 13. Decisões pendentes

Descoberta extensível de roots fornecidos pelos providers, captura de stacks completos de packs e consistência forte com alteração concorrente.

## 14. Relação com outras specs

Produz 001 snapshots para 004; configuração em 008.
