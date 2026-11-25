# Submission

## Investigation and reproduction

### Environment and baseline

- Windows 10 Pro with Windows PowerShell 5.1.
- Node.js 24.22.3.
- JDK 25.0.4.1 (Temurin).
- Maven 3.9.9 through the supplied wrapper.
- PostgreSQL 17. The README lists 15.18; nothing in the project depends on the difference.

I set the project up with `.\scripts\setup-db.ps1` and `.\scripts\start.ps1`, as the README describes.

Before changing anything I ran `.\scripts\test.ps1`. It passed: 1 backend unit test, 2 backend integration tests, 6 frontend tests, typecheck, lint and build. The existing smoke tests therefore did not catch any of the problems below.

### How I investigated

1. I used the application in the browser against the real backend and PostgreSQL.
2. I sent direct HTTP requests to the API, and a few through the Next.js proxy on port 3000, and compared the status, the body and the stored data with the assignment.
3. For every discrepancy I read the code path to find the cause before changing it.

Every case that led to a fix is now an automated test.

### Findings

Each finding lists the expected behavior (from the assignment), the actual behavior and the cause. The last column names the automated test that now covers it.

#### Backend API

| # | Reproduction | Expected | Actual | Cause | Covered by |
| --- | --- | --- | --- | --- | --- |
| B1 | `PUT /api/persons/1` with four new values, then `GET /api/persons/1` or refresh the page | Changes persist | The PUT response shows the new values, but the stored record is unchanged | `PersonService.update()` called `this.applyChanges()`. The `@Transactional` on `applyChanges` is ignored for a self-call (Spring proxy), and with Open Session in View disabled nothing flushed the entity | `PersonApiIT.updateReplacesAllFourFieldsKeepsTheIdAndPersists` |
| B2 | `PUT` that changes only the email | All four business fields are replaced | The email never changes, in the response or in the database | `PersonMapper.updateEntity()` copied first name, last name and date of birth, but not email | Same test, `PersonMapperTest` |
| B3 | `GET`, `PUT` or `DELETE /api/persons/999999999` | `404 PERSON_NOT_FOUND` | `500 INTERNAL_ERROR` | `PersonNotFoundException` had no handler and reached the catch-all 500 handler | `missingPersonReturns404ForReadUpdateAndDelete` |
| B4 | `POST {}`, a blank name, an invalid email, a 101-character name or a future date | `400 VALIDATION_FAILED` with `fieldErrors` | `500 INTERNAL_ERROR` for every field validation failure, on POST and PUT | `MethodArgumentNotValidException` had no handler | `invalidFieldsReturnOneMessagePerFieldAndStoreNothing`, `rejectedUpdatesLeaveTheStoredPersonUnchanged` |
| B5 | `POST` with today's UTC date as date of birth | Accepted ("today or earlier") | Rejected (as a 500, because of B4) | `@Past` excludes today | `acceptsTodayInUtcAndRejectsTomorrow` (API and `PersonRequestTest`) |
| B6 | First name of exactly 100 characters and email of exactly 254 characters, made of less common Unicode characters | Accepted (the limits are 100 and 254 characters) | Rejected (500, see B4) | Length was counted in UTF-16 units, so these characters counted twice | `countsLengthsInCharactersRatherThanUtf16Units` |
| B7 | First name made only of unusual space characters (for example copied from a web page); such spaces around a name or email, or inside an email | Blank after trimming is "required"; surrounding whitespace is trimmed; no whitespace inside an email | Stored as entered (201) | The trimming and the email pattern recognized only ASCII whitespace | `storesUnicodeNamesTrimmedTextAndDuplicateEmails`, `PersonRequestTest` |
| B8 | A valid person JSON followed by extra text, such as a second `}`, or two JSON objects back to back | `400 INVALID_REQUEST`, nothing stored | 201 and a new row | Jackson ignores content after the first value by default | `malformedBodiesReturnInvalidRequestAndStoreNothing` |
| B9 | `POST` with `Content-Type: text/plain`, none at all, `*/*`, XML, form or multipart | `400` | `500 INTERNAL_ERROR` | `HttpMediaTypeNotSupportedException`, `MultipartException` and the `IllegalArgumentException` Spring throws for a wildcard type were not handled | `nonJsonContentTypesReturnInvalidRequestAndStoreNothing` |
| B10 | A body with a repeated key, for example `firstName` twice | `400 INVALID_REQUEST` | 500 when the repeated key came after all four fields; an earlier repeat was silently accepted (last value wins) | Duplicate detection was off | `malformedBodies...` (duplicate key case) |
| B11 | `POST` a valid person with `Accept: text/html` | A rejected request leaves stored data unchanged | `406` with an HTML error page, and the person was created anyway | The controller did not declare `produces`, so content negotiation failed only when the response was written, after the insert | `nonJsonAcceptHeaderIsRejectedBeforeAnythingIsStored` |
| B12 | Save a very old date of birth, such as `1582-10-10` or `1300-02-28`, then read it back several times | The same date is returned | A different date comes back (`1582-10-10` became `1582-10-20`), and `1300-02-28` made the whole list fail (`GET /api/persons` returns 500). The errors appear only after a few reads | Java's old date class uses a different calendar before 1582 than PostgreSQL | `historicalDatesAreStoredAndReadBackUnchanged` |
| B13 | `{invalid`, an unknown `id` property, `/api/persons/abc` | A useful `INVALID_REQUEST` message | Every malformed request answered "Enter valid values using YYYY-MM-DD for dates", even when no date was involved | A single handler with a fixed message covered all malformed input | `malformedBodies...`, `nonNumericIdsReturnInvalidRequest` |
| B14 | `"firstName": 123` or `"lastName": true` | `400` (malformed input) | Stored as the strings `"123"` and `"true"` | Jackson coerces scalars to strings by default | `malformedBodies...` |

These requirements already worked on the original code:

- The list is ordered by ID.
- `POST` returns 201 with a `Location` header.
- `DELETE` returns 204 with an empty body.
- Duplicate emails are accepted.
- An unknown property or a malformed date returns 400.
- Unknown paths return the JSON 404 and unsupported methods return the JSON 405.

#### Frontend

| # | Reproduction | Expected | Actual | Cause | Covered by |
| --- | --- | --- | --- | --- | --- |
| F1 | Enter `Zoë`, `O'Brien`, `Anne-Marie` or `Đorđe` in the form | Unicode, spaces, apostrophes and hyphens are accepted | "Use letters and spaces only" | The client rule was `^[A-Za-z ]+$`. The API had no character rule at all, so the two disagreed | `persons.test.ts` "Names" |
| F2 | Paste a long name, or one with less common Unicode characters | Limit of 100 (254) characters with a clear message | `maxLength` silently cut pasted text, and lengths were counted in UTF-16 units | `maxLength` attribute and `String.length` | "Names", "Email" |
| F3 | Save the email `anna@bücher.example` | Spelling is preserved apart from trimming | Saved as `anna@xn--bcher-kva.example` | `<input type="email">` makes Chromium convert the domain to punycode | "Email" (keeps international domains) |
| F4 | Delete a person and confirm | Success | The person is deleted (204), but the page shows an error | `apiClient` called `response.json()` on the empty 204 body | "API client" |
| F5 | Open `/persons/1?saved=updated`, or refresh the detail page after saving | Success is shown only after the server confirms the operation | "Person updated successfully" appears whenever the query parameter is present | The banner was driven by the URL | "Saved notice" |
| F6 | Open `/persons/999999` or `/persons/abc` | An appropriate error state | "Something went wrong. Please try again." or the date-format message from B13, plus a "Try again" button that cannot help | Backend B3/B13, and the page offered a retry for every error | "API client" and manual check |
| F7 | Type `02/31/2020` into the date field and save | "Enter a valid calendar date" | "Enter a date of birth" | The browser reports an impossible date as an empty value | "Date of birth" |

## Changes

The work is split into three branches, each merged into `main`: `fix/backend-api`, `fix/frontend-form` and `refactor/person-mapper`. No dependencies were added. `@CodePointLength` comes from Hibernate Validator, which was already on the classpath. There are no schema migrations and no new environment variables. No setup changes are needed beyond the README.

### Backend (`backend/src/main`)

- `person/PersonService.java`: `update()` itself is `@Transactional`; the self-invoked `applyChanges()` is removed (B1).
- `person/PersonMapper.java`: the update path now sets the email (B2). See also the refactoring below.
- `person/PersonRequest.java`, which holds the validation rules:
  - `@PastOrPresent` with the message "Date of birth cannot be in the future". It uses the existing UTC `ClockConfiguration` (B5).
  - `@CodePointLength` for the 100/254 limits (B6).
  - Names accept letters from any script, spaces, apostrophes and hyphens. Other characters are rejected with a clear message.
  - Trimming removes Unicode whitespace and control characters (B7).
  - The email pattern uses `(?U)`, so Unicode whitespace inside the address is rejected (B7).
- `shared/ApiExceptionHandler.java`:
  - Field validation returns `VALIDATION_FAILED` with one message per field. "Required" wins over "too long", which wins over format (B4).
  - A missing person returns `PERSON_NOT_FOUND` (B3).
  - Malformed bodies get a message for each kind of problem (B13): invalid JSON, an unknown property, a bad date, or a non-text value for a name or email.
  - A non-numeric ID returns "Use a numeric person ID".
  - Non-JSON content types return 400 (B9).
  - A non-JSON `Accept` header gets a JSON `406` (B11).
  - The existing 404, 405 and 500 handlers are unchanged.
- `person/PersonController.java`: `produces = application/json`, so a request that cannot receive JSON is rejected before the controller method runs and nothing is stored (B11).
- `shared/IsoDateDeserializer.java`: rejects year 0000 (see Assumptions).
- `shared/JacksonConfiguration.java` (new): name and email fields accept only JSON strings (B14).
- `application.properties`:
  - `hibernate.type.java_time_use_direct_jdbc=true` (B12).
  - `fail-on-trailing-tokens` (B8).
  - `strict-duplicate-detection` (B10).

### Frontend (`frontend/src`)

- `features/persons/personValidation.ts`:
  - The same name rule as the backend.
  - Lengths are counted in characters (code points).
  - An email that is too long gets its own message.
  - A partial or impossible date typed into the date field gets "Enter a valid calendar date" (F1, F2, F7).
- `features/persons/PersonForm.tsx`:
  - No `maxLength`.
  - The email field is `type="text"` with `inputMode="email"`, so mobile keyboards stay the same while the spelling is preserved (F3).
  - The form detects `validity.badInput` on the date field (F7).
  - The success notice is remembered only after the server responds (F5).
- `features/persons/savedNotice.ts` (new): a one-time, in-memory notice handed from the form to the detail page. A refresh or a typed URL never shows it (F5).
- `features/persons/PersonDetail.tsx`, `usePerson.ts`: show the server message and hide "Try again" for 404/400, where retrying cannot help (F6).
- `lib/apiClient.ts`: treat 204 as success without parsing a body (F4).

### Tests

- `backend/src/test/.../PersonRequestTest.java`: extended from 1 to 23 tests covering Unicode trimming, blank values, names, lengths, email whitespace, and today and tomorrow in UTC.
- `backend/src/test/.../PersonApiIT.java` (new, 33 tests):
  - Uses raw HTTP against the real application and the PostgreSQL test database.
  - Covers every backend finding above and checks the row count or the stored record after each rejected request.
- `backend/src/test/.../PersonMapperTest.java` (new, 2 tests): covers the refactoring.
- `frontend/tests/persons.test.ts` (new, 30 tests): covers validation, the API client and the saved notice.
- The supplied smoke tests are unchanged.

### Assumptions and decisions

- **Names and digits.** The assignment lists what must be accepted in a name but does not say what must be rejected, and it does not mention digits. The original form allowed only A–Z and spaces, while the API accepted anything. I kept one rule on both sides: Unicode letters and combining marks, spaces, apostrophes and hyphens. That means `Edit1`, `Anna!` or markup is rejected with a clear message. If digits should be allowed, the change is one character class in `PersonRequest.NAME_PATTERN` and in `NAME` in `personValidation.ts`.
- **"Characters"** means Unicode code points. A rare character that Java stores in two units still counts as one.
- **Year 0000 is rejected (400).** The assignment asks for a valid calendar date but does not mention year 0. I reject it for three reasons:
  - The browser date field does not accept it.
  - PostgreSQL stores it as 1 BC.
  - The PostgreSQL driver could not read such a row back, which made the whole list return 500.

  Accepted dates are therefore `0001-01-01` up to today (UTC).
- **`fieldErrors` with `INVALID_REQUEST`.** An unparsable date or a non-string name is malformed input, so the code is `INVALID_REQUEST`. The response still names the field (for example `{"dateOfBirth": "Enter a valid calendar date"}`) so the form can show the message next to it. All other `INVALID_REQUEST` responses use an empty `fieldErrors`.
- **406 for a non-JSON `Accept` header.** The assignment does not cover this case. The API answers `406` with an `INVALID_REQUEST` JSON body and stores nothing. This applies to every endpoint, including requests for a missing person.
- **Today in UTC.** The server uses the existing UTC `Clock`, and the browser compares against `new Date().toISOString()`, which is also UTC.

## Focused refactoring

**What.** `PersonMapper.createEntity()` now creates a `Person` and delegates to `updateEntity()`. The four business fields are copied in exactly one method instead of two (`backend/src/main/java/com/example/people/person/PersonMapper.java`).

**Why this area.** The two methods each listed the same setters, and that duplication caused a real defect. The update copy left out `setEmail`, which is finding B2: email could never be edited. I committed the bug fix (adding `setEmail`) first, with the other backend fixes. The refactoring is a separate commit that removes the duplication, so the next field added to `Person` cannot be forgotten in one of the two paths. It is small, contained in one class, and changes no public behavior.

**Behavior preserved.** `PersonMapperTest` checks that create copies all four fields and leaves the ID empty, and that update replaces all four. The API integration tests for create and update pass unchanged.

**Considered and not chosen.** The create and edit submit handlers in `PersonForm.tsx` are also similar. I left them alone: that duplication has not caused a defect, the file had already changed for the frontend fixes, and the assignment asks for one refactoring.

## Verification commands and results

All results below are from 25 November 2026, on the final code.

### Automated checks

From the repository root, with `JAVA_HOME` pointing to JDK 25 and Node.js 24 and PostgreSQL's `bin` on `PATH`:

```powershell
.\scripts\test.ps1
```

| Check | Result |
| --- | --- |
| Backend unit tests (`PersonRequestTest` 23, `PersonMapperTest` 2) | 25 passed |
| Backend integration tests against `person_training_test` (`PersonApiIT` 33, `PersonApiSmokeIT` 2) | 35 passed, `BUILD SUCCESS` |
| Frontend tests (`persons.test.ts` 30, `smoke.test.ts` 6) | 36 passed |
| `npm run typecheck` | passed |
| `npm run lint` | passed, no findings |
| `npm run build` | passed |
| Script exit code | 0 |

Note: `npm run build` rewrites `frontend/next-env.d.ts` to point at `.next/types`. Running `start.ps1` (or `npm run dev`) restores it. The committed file is unchanged.

### Manual checks in the browser

I started the application with `.\scripts\start.ps1` and used a Chromium-based browser with an en-US date field (MM/DD/YYYY). The backend ran against the real PostgreSQL database.

| Check | Result |
| --- | --- |
| Create with `Edit1`, an empty last name, `anna@example` and `02/31/2020` | One message per field, including "Enter a valid calendar date". All values kept |
| Same form with a future date (tomorrow, UTC) | "Date of birth cannot be in the future" |
| Create `  Zoë  ` / `D’Angelo-Łukasz` / an email that another person already uses / today | Saved, trimmed, success shown after the server answered. Refresh shows the same data without the banner |
| Edit all four fields (`Anne-Marie`, `O'Brien`, `Anne@Bücher.example`, `02/29/1980`) | Saved with the same ID, email spelling preserved, persisted after refresh |
| Open `/persons/{id}?saved=updated` directly | No success banner |
| Delete, then Cancel | Nothing changes |
| Delete, then confirm | "Person deleted successfully". The person is gone after refresh. The detail URL shows "Person not found" without "Try again" |
| `/persons/abc` | "Use a numeric person ID" |
| Edit form open while the person is deleted through the API, then Save | "Person not found", all entered values kept |
| Backend stopped: list page, and Save on the create form | List shows an error with "Try again". The form shows an error and keeps every value |
| Backend on an empty database | Empty state "No people yet" with an "Add person" link |

## Remaining work

### Known issues not fixed

I found these but left them out to keep the changes focused. Each one is either rare in normal use or needs a larger change than its effect justifies.

- **Double submit.** Two very fast clicks on Save can create the same person twice. I could trigger it only with scripted clicks.
- **Delete dialog.** Pressing Esc twice while a delete is running can leave the buttons unresponsive until the page is reloaded.
- **Email with unusual characters.** An email with a NUL character returns a safe `500` instead of `400`, and an unpaired surrogate is stored as `?`.
- **Concurrent deletes.** If two `DELETE` requests for the same person arrive together, the second can receive a safe `500` instead of `404`.
- **Slow email pattern.** An email such as `a@` followed by 20,000 dots and an `@` takes about 2 s to check (about 9 s with 40,000 dots). This is reachable only through direct API requests, because the form rejects anything over 254 characters.

### Noticed, documented only

Minor observations (the `Allow` header on 405 responses, lenient path IDs such as `/api/persons/05`, invalid UTF-8 in request bodies, Tomcat's HTML error page for malformed URLs, invisible characters and unusual dots in emails) are not fixed and are not covered by the assignment.

### Not verified

- Only Windows 10 with PowerShell 5.1, PostgreSQL 17 (not 15.18) and one Chromium-based browser were used. The `.sh` launchers, other browsers, date-field locales and Linux/macOS were not tried.
- Loading states were not checked on their own. Against a local backend they are visible only for a moment.
- The UTC "today" boundary is covered by tests with a UTC clock, but I did not check it live around midnight UTC.
