package com.example.people.person;

import com.example.people.DatabaseTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/** Requirement checks for the person API, sent as raw HTTP so headers and bodies are exactly as written. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonApiIT extends DatabaseTestSupport {
    private static final String VALID = "{\"firstName\":\"Anna\",\"lastName\":\"Novak\",\"email\":\"anna@example.com\",\"dateOfBirth\":\"1994-05-12\"}";
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    record Reply(int status, String contentType, String text, JsonNode body) {}

    @BeforeEach
    void prepareData() { jdbc.execute("TRUNCATE TABLE person RESTART IDENTITY"); }

    @Test
    void updateReplacesAllFourFieldsKeepsTheIdAndPersists() throws Exception {
        long id = create("Anna", "Novak", "anna@example.com", "1994-05-12");
        var updated = sendJson("PUT", "/api/persons/" + id, person("Zoë", "O'Brien-Smith", "Zoe.OBrien@Example.org", "1980-02-29"));
        assertThat(updated.status()).isEqualTo(200);
        assertPerson(updated.body(), id, "Zoë", "O'Brien-Smith", "Zoe.OBrien@Example.org", "1980-02-29");
        assertPerson(sendJson("GET", "/api/persons/" + id, null).body(), id, "Zoë", "O'Brien-Smith", "Zoe.OBrien@Example.org", "1980-02-29");
        assertThat(jdbc.queryForObject("SELECT email FROM person WHERE id = ?", String.class, id)).isEqualTo("Zoe.OBrien@Example.org");
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void missingPersonReturns404ForReadUpdateAndDelete() throws Exception {
        for (var reply : List.of(sendJson("GET", "/api/persons/999", null), sendJson("PUT", "/api/persons/999", VALID), sendJson("DELETE", "/api/persons/999", null))) {
            assertError(reply, 404, "PERSON_NOT_FOUND", "Person not found", Map.of());
        }
        assertThat(rowCount()).isZero();
    }

    @Test
    void invalidFieldsReturnOneMessagePerFieldAndStoreNothing() throws Exception {
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        assertError(sendJson("POST", "/api/persons", person("   ", "Edit1", "anna@example", tomorrow)), 400, "VALIDATION_FAILED", "Please check the entered values", Map.of(
                "firstName", "Enter a first name",
                "lastName", "Use letters, spaces, apostrophes and hyphens only",
                "email", "Enter a valid email address",
                "dateOfBirth", "Date of birth cannot be in the future"));
        assertError(sendJson("POST", "/api/persons", "{}"), 400, "VALIDATION_FAILED", "Please check the entered values", Map.of(
                "firstName", "Enter a first name",
                "lastName", "Enter a last name",
                "email", "Enter an email address",
                "dateOfBirth", "Enter a date of birth"));
        assertError(sendJson("POST", "/api/persons", person("Anna", "Novak", "", "1994-05-12")), 400, "VALIDATION_FAILED", "Please check the entered values",
                Map.of("email", "Enter an email address"));
        assertError(sendJson("POST", "/api/persons", person("A".repeat(101), "Novak", "a".repeat(250) + "@example.com", "1994-05-12")), 400, "VALIDATION_FAILED",
                "Please check the entered values", Map.of("firstName", "Use 100 characters or fewer", "email", "Use 254 characters or fewer"));
        assertThat(rowCount()).isZero();
    }

    @Test
    void rejectedUpdatesLeaveTheStoredPersonUnchanged() throws Exception {
        long id = create("Anna", "Novak", "anna@example.com", "1994-05-12");
        assertThat(sendJson("PUT", "/api/persons/" + id, person("", "Novak", "not-an-email", "1994-05-12")).status()).isEqualTo(400);
        assertThat(sendJson("PUT", "/api/persons/" + id, "{invalid").status()).isEqualTo(400);
        assertThat(sendJson("PUT", "/api/persons/" + id, person("Zoë", "Novak", "zoe@example.com", "2020-02-31")).status()).isEqualTo(400);
        assertPerson(sendJson("GET", "/api/persons/" + id, null).body(), id, "Anna", "Novak", "anna@example.com", "1994-05-12");
    }

    @Test
    void acceptsTodayInUtcAndRejectsTomorrow() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(sendJson("POST", "/api/persons", person("Anna", "Novak", "anna@example.com", today.toString())).status()).isEqualTo(201);
        assertError(sendJson("POST", "/api/persons", person("Anna", "Novak", "anna@example.com", today.plusDays(1).toString())), 400, "VALIDATION_FAILED",
                "Please check the entered values", Map.of("dateOfBirth", "Date of birth cannot be in the future"));
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void storesUnicodeNamesTrimmedTextAndDuplicateEmails() throws Exception {
        String nbsp = cp(0x00A0), ideographicSpace = cp(0x3000);
        var created = sendJson("POST", "/api/persons", person(nbsp + "Zoë" + ideographicSpace, " D’Angelo-Łukasz ", "\t Anna.Novak@Example.COM" + nbsp, "1994-05-12"));
        assertThat(created.status()).isEqualTo(201);
        assertPerson(created.body(), 1, "Zoë", "D’Angelo-Łukasz", "Anna.Novak@Example.COM", "1994-05-12");
        assertThat(sendJson("POST", "/api/persons", person("Anna", "Novak", "Anna.Novak@Example.COM", "1994-05-12")).status()).isEqualTo(201);
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void countsLengthsInCharactersRatherThanUtf16Units() throws Exception {
        String letter = cp(0x20000); // one character, two UTF-16 code units
        var created = sendJson("POST", "/api/persons", person(letter.repeat(100), "Novak", letter.repeat(242) + "@example.com", "1994-05-12"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("firstName").asText()).isEqualTo(letter.repeat(100));
        assertError(sendJson("POST", "/api/persons", person(letter.repeat(101), "Novak", letter.repeat(243) + "@example.com", "1994-05-12")), 400, "VALIDATION_FAILED",
                "Please check the entered values", Map.of("firstName", "Use 100 characters or fewer", "email", "Use 254 characters or fewer"));
        assertThat(rowCount()).isEqualTo(1);
    }

    static Stream<Arguments> malformedBodies() {
        String general = "Send a valid JSON object in the request body";
        return Stream.of(
                Arguments.of("{invalid", general, Map.of()),
                Arguments.of(VALID + " garbage", general, Map.of()),
                Arguments.of(VALID + VALID, general, Map.of()),
                Arguments.of("{\"firstName\":\"Anna\",\"firstName\":\"Zoë\",\"lastName\":\"Novak\",\"email\":\"anna@example.com\",\"dateOfBirth\":\"1994-05-12\"}", general, Map.of()),
                Arguments.of("", general, Map.of()),
                Arguments.of("null", general, Map.of()),
                Arguments.of("[]", general, Map.of()),
                Arguments.of(VALID.replace("\"Anna\"", "123"), "Send names and email as text values", Map.of("firstName", "Enter a text value")),
                Arguments.of(VALID.replace("\"Novak\"", "true"), "Send names and email as text values", Map.of("lastName", "Enter a text value")),
                Arguments.of(VALID.replace("1994-05-12", "2020-02-31"), "Enter the date of birth as a valid YYYY-MM-DD date", Map.of("dateOfBirth", "Enter a valid calendar date")),
                Arguments.of(VALID.replace("1994-05-12", "0000-02-29"), "Enter the date of birth as a valid YYYY-MM-DD date", Map.of("dateOfBirth", "Enter a valid calendar date")),
                Arguments.of(VALID.replace("1994-05-12", "12.05.1994"), "Enter the date of birth as a valid YYYY-MM-DD date", Map.of("dateOfBirth", "Enter a valid calendar date")),
                Arguments.of(VALID.replace("\"1994-05-12\"", "19940512"), "Enter the date of birth as a valid YYYY-MM-DD date", Map.of("dateOfBirth", "Enter a valid calendar date")),
                Arguments.of(VALID.replace("}", ",\"id\":7}"), "Remove fields that are not part of a person", Map.of()));
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedBodiesReturnInvalidRequestAndStoreNothing(String body, String message, Map<String, String> fieldErrors) throws Exception {
        assertError(sendJson("POST", "/api/persons", body), 400, "INVALID_REQUEST", message, fieldErrors);
        assertThat(rowCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "", "*/*", "application/xml", "application/x-www-form-urlencoded", "multipart/form-data"})
    void nonJsonContentTypesReturnInvalidRequestAndStoreNothing(String contentType) throws Exception {
        var reply = contentType.isEmpty()
                ? send("POST", "/api/persons", VALID, "Accept", "application/json")
                : send("POST", "/api/persons", VALID, "Content-Type", contentType, "Accept", "application/json");
        assertError(reply, 400, "INVALID_REQUEST", "Send the request body as JSON", Map.of());
        assertThat(rowCount()).isZero();
    }

    @Test
    void nonJsonAcceptHeaderIsRejectedBeforeAnythingIsStored() throws Exception {
        var reply = send("POST", "/api/persons", VALID, "Content-Type", "application/json", "Accept", "text/html");
        assertThat(reply.status()).isEqualTo(406);
        assertThat(reply.body().get("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(rowCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void nonNumericIdsReturnInvalidRequest(String id) throws Exception {
        assertError(sendJson("GET", "/api/persons/" + id, null), 400, "INVALID_REQUEST", "Use a numeric person ID", Map.of());
    }

    @Test
    void historicalDatesAreStoredAndReadBackUnchanged() throws Exception {
        List<String> dates = List.of("0001-01-01", "1100-02-27", "1100-02-28", "1300-02-28", "1582-10-10", "1582-10-15", "1900-02-28", "2000-02-29");
        Map<Long, String> stored = new LinkedHashMap<>();
        for (String date : dates) {
            var created = sendJson("POST", "/api/persons", person("Anna", "Novak", "anna@example.com", date));
            assertThat(created.status()).as(date).isEqualTo(201);
            assertThat(created.body().get("dateOfBirth").asText()).as(date).isEqualTo(date);
            stored.put(created.body().get("id").asLong(), date);
        }
        // Repeat the reads so the PostgreSQL driver switches to server-prepared (binary) statements.
        for (int round = 0; round < 8; round++) {
            for (var entry : stored.entrySet()) {
                var read = sendJson("GET", "/api/persons/" + entry.getKey(), null);
                assertThat(read.status()).isEqualTo(200);
                assertThat(read.body().get("dateOfBirth").asText()).as("round " + round).isEqualTo(entry.getValue());
            }
            var list = sendJson("GET", "/api/persons", null);
            assertThat(list.status()).isEqualTo(200);
            List<String> listed = new ArrayList<>();
            list.body().forEach(person -> listed.add(person.get("dateOfBirth").asText()));
            assertThat(listed).as("round " + round).containsExactlyElementsOf(dates);
        }
    }

    @Test
    void listIsOrderedById() throws Exception {
        create("Zed", "Last", "z@example.com", "1990-01-01");
        create("Anna", "First", "a@example.com", "1980-01-01");
        create("Mia", "Middle", "m@example.com", "1985-01-01");
        List<Long> ids = new ArrayList<>();
        sendJson("GET", "/api/persons", null).body().forEach(person -> ids.add(person.get("id").asLong()));
        assertThat(ids).containsExactly(1L, 2L, 3L);
    }

    private long create(String firstName, String lastName, String email, String dateOfBirth) throws Exception {
        var created = sendJson("POST", "/api/persons", person(firstName, lastName, email, dateOfBirth));
        assertThat(created.status()).isEqualTo(201);
        return created.body().get("id").asLong();
    }

    private String person(String firstName, String lastName, String email, String dateOfBirth) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("email", email);
        body.put("dateOfBirth", dateOfBirth);
        return json.writeValueAsString(body);
    }

    private Reply sendJson(String method, String path, String body) throws Exception {
        return body == null
                ? send(method, path, null, "Accept", "application/json")
                : send(method, path, body, "Content-Type", "application/json", "Accept", "application/json");
    }

    private Reply send(String method, String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, UTF_8))
                .headers(headers)
                .build();
        var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        JsonNode parsed = contentType.startsWith("application/json") && !response.body().isEmpty() ? json.readTree(response.body()) : null;
        return new Reply(response.statusCode(), contentType, response.body(), parsed);
    }

    private void assertError(Reply reply, int status, String code, String message, Map<String, String> fieldErrors) {
        assertThat(reply.status()).as(reply.text()).isEqualTo(status);
        assertThat(reply.body()).as(reply.text()).isNotNull();
        assertThat(reply.body().get("code").asText()).isEqualTo(code);
        assertThat(reply.body().get("message").asText()).isEqualTo(message);
        assertThat(reply.body().get("fieldErrors")).isEqualTo(json.valueToTree(fieldErrors));
    }

    private static void assertPerson(JsonNode person, long id, String firstName, String lastName, String email, String dateOfBirth) {
        assertThat(person.get("id").asLong()).isEqualTo(id);
        assertThat(person.get("firstName").asText()).isEqualTo(firstName);
        assertThat(person.get("lastName").asText()).isEqualTo(lastName);
        assertThat(person.get("email").asText()).isEqualTo(email);
        assertThat(person.get("dateOfBirth").asText()).isEqualTo(dateOfBirth);
    }

    private int rowCount() { return jdbc.queryForObject("SELECT count(*) FROM person", Integer.class); }

    private static String cp(int... codePoints) { return new String(codePoints, 0, codePoints.length); }
}
