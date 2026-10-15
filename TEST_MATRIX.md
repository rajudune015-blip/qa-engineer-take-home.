# Test Matrix — Offline Survey App

| ID | Area | Scenario | Expected result | Priority | Automation |
|---|---|---|---|---|---|
| SYNC-01 | API | Same local ID from two devices | Both submissions accepted; neither overwrites the other | P0 | API |
| SYNC-02 | API | Same submission retried unchanged | Idempotent success; no duplicate server record | P0 | API |
| SYNC-03 | API | Same submission ID with changed payload | Conflict response; original data preserved | P0 | API |
| SYNC-04 | Mobile | Network lost during 99% photo upload | Pending state retained; retry resumes/restarts safely | P0 | Device + proxy |
| SYNC-05 | Mobile | HTTP 500 during sync | Local data remains; retry is scheduled with backoff | P0 | Device + proxy |
| SYNC-06 | Mobile | App killed during offline capture | Draft recovers with correct answers and attachments | P0 | Device |
| SYNC-07 | Load | Large synchronized arrival spike | Error/latency stay within agreed SLO; service recovers | P0 | k6/Gatling |
| FORM-01 | Web/mobile | q_3 hobby=Yes | Follow-up question visible and required | P1 | Playwright |
| FORM-02 | Web/mobile | q_3 changes Yes to No | Follow-up hidden; stale answer follows defined clear/retain policy | P1 | Playwright |
| FORM-03 | Schema | Unsupported question type/operator | Clear validation error or safe fallback; no crash | P1 | Contract |
| FORM-04 | Roster | 0, 1, and many household members | Correct rendering, answer isolation, and persistence | P1 | API/UI |
| FORM-05 | Compatibility | Older app downloads newer schema | Compatible fields work; unsupported required feature fails safely | P0 | Integration |
| HFC-01 | Logic | Age=14 and Education=College | Re-ask and flag | P1 | Unit/integration |
| HFC-02 | Logic | Age=15 and Education=College | No trigger for strict Age < 15 rule | P1 | Unit |
| HFC-03 | Logic | Repeated invalid response | No infinite loop; defined attempt cap/escalation | P0 | Unit/device |
| PERF-01 | Android | Play 4 offline videos on 4 GB device | No ANR/crash; bounded memory and storage use | P0 | Device/perf |
| UX-01 | Accessibility | 320 dp width and large font | Text/actions not clipped; Next discoverable | P1 | Manual + checks |
| UX-02 | Accessibility | Bright light / low contrast | Controls and status remain perceivable | P1 | Field/manual |
