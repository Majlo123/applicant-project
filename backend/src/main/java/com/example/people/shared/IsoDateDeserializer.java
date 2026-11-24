package com.example.people.shared;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class IsoDateDeserializer extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        String text = parser.getText();
        try {
            if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("Use YYYY-MM-DD", text, 0);
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(parser, "Enter a valid YYYY-MM-DD calendar date", text, LocalDate.class);
        }
    }
}
