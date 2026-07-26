# Client RecipeManager — investigação

O `ClientPacketListener` agenda a substituição do `RecipeManager` na thread
principal do cliente. A preparação server-side não consegue confirmar esse
momento sem protocolo adicional.
