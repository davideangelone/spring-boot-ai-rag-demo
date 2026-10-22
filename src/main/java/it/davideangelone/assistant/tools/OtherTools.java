package it.davideangelone.assistant.tools;

import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OtherTools {

    @Tool(description = "Restituisce data e ora attuale")
    LocalDateTime getCurrentDateTime() {
        log.info("[TOOL] Usato tool getCurrentDateTime");
        return LocalDateTime.now();
    }

}
