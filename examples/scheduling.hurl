# Programme scheduling: our own examples (workflow/specs/scheduling.md), happy path and the decided edge cases.
# Run: hurl --test --variable base_url=http://localhost:8080 scheduling.hurl
# Uses R3/S5 and R2/S5 and R3/S4, and empties them again at the end.

# Setup: two speakers; an accepted presentation, an accepted workshop, a submitted talk,
# and an accepted talk whose co-speaker is the first speaker
POST {{base_url}}/speakers
{ "name": "Planner A", "email": "prog-a-{{newUuid}}@example.com" }
HTTP 201
[Captures]
a_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Planner B", "email": "prog-b-{{newUuid}}@example.com" }
HTTP 201
[Captures]
b_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "On the programme", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{a_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Long one", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{a_id}} }
HTTP 201
[Captures]
ws_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Not decided", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{b_id}} }
HTTP 201
[Captures]
pending_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Shares a speaker", "format": "LIGHTNING_20", "level": "BEGINNER", "speakerId": {{b_id}} }
HTTP 201
[Captures]
clash_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{clash_id}}/co-speakers/{{a_id}}
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/301
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/302
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/ratings/301
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/ratings/302
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/talks/{{clash_id}}/ratings/301
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{clash_id}}/ratings/302
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{clash_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# Put a talk in R3 S5, look it up
PUT {{base_url}}/programme/R3/S5
{ "talkId": {{talk_id}} }
HTTP 200
[Asserts]
jsonpath "$.room" == "R3"
jsonpath "$.slot" == "S5"
jsonpath "$.start" == "15:45"
jsonpath "$.end" == "16:45"
jsonpath "$.talkId" == {{talk_id}}

GET {{base_url}}/programme/R3/S5
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{talk_id}}

# Room and slot taken; talk already elsewhere: 409
PUT {{base_url}}/programme/R3/S5
{ "talkId": {{clash_id}} }
HTTP 409

PUT {{base_url}}/programme/R1/S1
{ "talkId": {{talk_id}} }
HTTP 409

# Same slot, other room, shares speaker A: 409
PUT {{base_url}}/programme/R2/S5
{ "talkId": {{clash_id}} }
HTTP 409

# Not accepted: 409. Workshop outside the long slot: 409, inside: 200
PUT {{base_url}}/programme/R2/S5
{ "talkId": {{pending_id}} }
HTTP 409

PUT {{base_url}}/programme/R2/S5
{ "talkId": {{ws_id}} }
HTTP 409

PUT {{base_url}}/programme/R3/S4
{ "talkId": {{ws_id}} }
HTTP 200
[Asserts]
jsonpath "$.start" == "13:30"
jsonpath "$.end" == "15:30"

# Bad requests
PUT {{base_url}}/programme/R4/S1
{ "talkId": {{talk_id}} }
HTTP 404

PUT {{base_url}}/programme/R2/S5
{}
HTTP 400

PUT {{base_url}}/programme/R2/S5
{ "talkId": 999999999 }
HTTP 400

# Take them out again
DELETE {{base_url}}/programme/R3/S5
HTTP 204

DELETE {{base_url}}/programme/R3/S4
HTTP 204

GET {{base_url}}/programme/R3/S5
HTTP 404

DELETE {{base_url}}/programme/R3/S5
HTTP 404
