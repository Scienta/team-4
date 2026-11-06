# Workshop registration: our own examples (workflow/specs/workshop-registration.md), happy path and edge cases.
# Run: hurl --test --variable base_url=http://localhost:8080 workshop-registration.hurl

# Setup: a speaker with an accepted workshop, a submitted workshop and an accepted presentation
POST {{base_url}}/speakers
{ "name": "Host", "email": "ws-host-{{newUuid}}@example.com" }
HTTP 201
[Captures]
host_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Hands-on", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
ws_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Not yet", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
pending_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Just a talk", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{ws_id}}/ratings/201
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/ratings/202
{ "score": 5 }
HTTP 200

# Not accepted yet: 409
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Early", "email": "early@example.com" }
HTTP 409

PUT {{base_url}}/talks/{{ws_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# Register two, list in order
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada", "email": "Ada@Example.com" }
HTTP 201
[Captures]
reg_id: jsonpath "$.id"
[Asserts]
jsonpath "$.workshopId" == {{ws_id}}
jsonpath "$.name" == "Ada"
jsonpath "$.email" == "Ada@Example.com"

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Bob", "email": "bob@example.com" }
HTTP 201

GET {{base_url}}/workshops/{{ws_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].id" == {{reg_id}}
jsonpath "$[0].email" == "Ada@Example.com"
jsonpath "$[1].name" == "Bob"

# Same email in another case: 409
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada again", "email": "ada@example.com" }
HTTP 409

# Missing or bad fields: 400
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "email": "nameless@example.com" }
HTTP 400

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "No mail" }
HTTP 400

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Bad mail", "email": "not-an-email" }
HTTP 400

# Submitted workshop: 409, empty list
POST {{base_url}}/workshops/{{pending_id}}/registrations
{ "name": "Ada", "email": "ada@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{pending_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Not a workshop: 409 (harness check-2); no such talk: 404
POST {{base_url}}/workshops/{{talk_id}}/registrations
{ "name": "Ada", "email": "ada@example.com" }
HTTP 409

GET {{base_url}}/workshops/999999999/registrations
HTTP 404

# Withdrawn: keeps its list, takes no more
POST {{base_url}}/talks/{{ws_id}}/withdraw
{ "speakerId": {{host_id}} }
HTTP 200

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Late", "email": "late@example.com" }
HTTP 409

GET {{base_url}}/workshops/{{ws_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
