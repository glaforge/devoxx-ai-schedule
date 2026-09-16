/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dvxaisched.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Factory
public class LangChain4jConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Singleton
    public ChatModel chatModel(
        @Value("${gemini.api-key:}") String apiKey,
        @Value("${gemini.model:gemini-3.8-flash}") String modelName
    ) {
        String effectiveApiKey = System.getenv("GEMINI_API_KEY");
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            effectiveApiKey = apiKey;
        }
        if (effectiveApiKey != null) {
            effectiveApiKey = effectiveApiKey.trim();
            while (effectiveApiKey.endsWith("}")) {
                effectiveApiKey = effectiveApiKey.substring(0, effectiveApiKey.length() - 1).trim();
            }
        }

        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            LOG.warn("No GEMINI_API_KEY configured! Ensure the GEMINI_API_KEY environment variable is set.");
        } else {
            String maskedKey = effectiveApiKey.length() > 6
                ? "..." + effectiveApiKey.substring(effectiveApiKey.length() - 4)
                : "configured";
            LOG.info("Configured GoogleGenAiChatModel for model '{}' with API key ({})", modelName, maskedKey);
        }

        return GoogleGenAiChatModel.builder()
            .apiKey(effectiveApiKey)
            .modelName(modelName != null && !modelName.isBlank() ? modelName : "gemini-3.8-flash")
            .temperature(0.2)
            .timeout(Duration.ofSeconds(120))
            .logRequests(false)
            .logResponses(false)
            .build();
    }
}
