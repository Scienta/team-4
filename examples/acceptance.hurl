# Acceptance: our own examples (workflow/specs/acceptance.md), happy path and the decided edge cases.
# Run: hurl --test --variable base_url=http://localhost:8080 acceptance.hurl

# Setup: a speaker, another speaker, three talks
POST {{base_url}}/speakers
{ "name": "Main", "email": "acc-main-{{newUuid}}@example.com" }
HTTP 201
[Captures]
main_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Other", "email": "acc-other-{{newUuid}}@example.com" }
HTTP 201
[Captures]
other_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "To be accepted", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"
[Asserts]
jsonpath "$.status" == "SUBMITTED"

POST {{base_url}}/talks
{ "title": "To be rejected", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk2_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "To be withdrawn", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk3_id: jsonpath "$.id"

# Accept with no ratings: 409
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 409

# Waitlist first
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.id" == {{talk_id}}
jsonpath "$.status" == "WAITLISTED"

# One reviewer twice is still one reviewer
PUT {{base_url}}/talks/{{talk_id}}/ratings/101
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/101
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 409

# Second reviewer: now it can be accepted
PUT {{base_url}}/talks/{{talk_id}}/ratings/102
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# Final: no further decisions
PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "REJECTED" }
HTTP 409

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# Bad decisions: 400
PUT {{base_url}}/talks/{{talk2_id}}/status
{ "status": "WITHDRAWN" }
HTTP 400

PUT {{base_url}}/talks/{{talk2_id}}/status
{}
HTTP 400

# Unknown talk: 404
PUT {{base_url}}/talks/999999999/status
{ "status": "REJECTED" }
HTTP 404

# Reject needs no ratings; a rejected talk can't be withdrawn
PUT {{base_url}}/talks/{{talk2_id}}/status
{ "status": "REJECTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"

POST {{base_url}}/talks/{{talk2_id}}/withdraw
{ "speakerId": {{main_id}} }
HTTP 409

# Somebody else can't withdraw: 403
POST {{base_url}}/talks/{{talk3_id}}/withdraw
{ "speakerId": {{other_id}} }
HTTP 403

POST {{base_url}}/talks/{{talk3_id}}/withdraw
{}
HTTP 400

# The speaker withdraws; then it's out for good
POST {{base_url}}/talks/{{talk3_id}}/withdraw
{ "speakerId": {{main_id}} }
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

POST {{base_url}}/talks/{{talk3_id}}/withdraw
{ "speakerId": {{main_id}} }
HTTP 409

PUT {{base_url}}/talks/{{talk3_id}}/status
{ "status": "WAITLISTED" }
HTTP 409

# An accepted talk can still be withdrawn by its speaker
POST {{base_url}}/talks/{{talk_id}}/withdraw
{ "speakerId": {{main_id}} }
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

# Status shows in the speaker's list
GET {{base_url}}/speakers/{{main_id}}/talks
HTTP 200
[Asserts]
jsonpath "$[1].id" == {{talk2_id}}
jsonpath "$[1].status" == "REJECTED"
