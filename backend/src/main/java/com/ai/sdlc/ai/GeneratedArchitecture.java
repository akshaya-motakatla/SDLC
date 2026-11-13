package com.ai.sdlc.ai;

import java.util.Map;

public record GeneratedArchitecture(
        String title,
        String architectureStyle,
        String content,
        Map<String, String> diagrams
) {
}
