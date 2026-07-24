package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

record FunctionResourceView(
        ResourceSnapshot snapshot,
        Map<ResourceLocation, FunctionSource> functions,
        Map<ResourceLocation, FunctionTagStack> tags
) {
    FunctionResourceView {
        functions = Map.copyOf(functions);
        tags = Map.copyOf(tags);
    }

    record FunctionSource(
            ResourceLocation id,
            ResourceLocation file,
            String packId,
            List<String> lines
    ) {
        FunctionSource {
            lines = List.copyOf(lines);
        }
    }

    record FunctionTagStack(ResourceLocation id, ResourceLocation file, List<TagLayer> layers) {
        FunctionTagStack {
            layers = List.copyOf(layers);
        }
    }

    record TagLayer(String packId, byte[] bytes) {
        TagLayer {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
