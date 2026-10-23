package it.davideangelone.assistant.tools;

import java.time.LocalDateTime;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class OtherTools implements AiTool{

    @Tool(description = "Restituisce data e ora attuale")
    LocalDateTime getCurrentDateTime() {
        return LocalDateTime.now();
    }

}
