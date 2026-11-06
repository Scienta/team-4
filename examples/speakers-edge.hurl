# Speakers: our own edge cases (workflow/specs/speakers.md). Not published by the harness.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/speakers-edge.hurl

# Register, with a mixed-case email and a non-ASCII name
POST {{base_url}}/speakers
{"name": "Ådne Øst", "email": "Ada-{{newUuid}}@Example.COM", "bio": "hei"}
HTTP 201
[Captures]
id: jsonpath "$.id"
email: jsonpath "$.email"

# Read back exactly as registered, with and without a trailing slash
GET {{base_url}}/speakers/{{id}}
HTTP 200
[Asserts]
jsonpath "$.name" == "Ådne Øst"
jsonpath "$.email" == "{{email}}"
jsonpath "$.bio" == "hei"

GET {{base_url}}/speakers/{{id}}/
HTTP 200
[Asserts]
jsonpath "$.id" == {{id}}
jsonpath "$.email" == "{{email}}"

# One email is one speaker, whatever the letter case, also through POST /speakers/
POST {{base_url}}/speakers/
{"name": "Someone else", "email": "{{email}}"}
HTTP 409

# The first registration is unchanged
GET {{base_url}}/speakers/{{id}}
HTTP 200
[Asserts]
jsonpath "$.name" == "Ådne Øst"

# A real name
POST {{base_url}}/speakers
{"name": "   ", "email": "blank-{{newUuid}}@example.com"}
HTTP 400

POST {{base_url}}/speakers
{"email": "noname-{{newUuid}}@example.com"}
HTTP 400

# A real email
POST {{base_url}}/speakers
{"name": "Ada"}
HTTP 400

POST {{base_url}}/speakers
{"name": "Ada", "email": "not-an-email-{{newUuid}}"}
HTTP 400

POST {{base_url}}/speakers
{"name": "Ada", "email": "ada-{{newUuid}}@localhost"}
HTTP 400

POST {{base_url}}/speakers
{"name": "Ada", "email": " ada-{{newUuid}}@example.com "}
HTTP 400

POST {{base_url}}/speakers/
{"name": "Ada", "email": "a da-{{newUuid}}@example.com"}
HTTP 400

# Bio at most 500 characters; an emoji is one character
POST {{base_url}}/speakers
{"name": "Bio", "email": "b500-{{newUuid}}@example.com", "bio": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
HTTP 201

POST {{base_url}}/speakers
{"name": "Bio", "email": "b501-{{newUuid}}@example.com", "bio": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
HTTP 400

POST {{base_url}}/speakers
{"name": "Bio", "email": "emoji-{{newUuid}}@example.com", "bio": "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"}
HTTP 201

# Unknown and malformed ids
GET {{base_url}}/speakers/999999999
HTTP 404

GET {{base_url}}/speakers/999999999/
HTTP 404

GET {{base_url}}/speakers/abc
HTTP 400

# The admin search treats its query as text
GET {{base_url}}/speakers/search
[Query]
q: '; DROP TABLE speaker;--
HTTP 200
[Asserts]
jsonpath "$" count == 0

GET {{base_url}}/speakers/{{id}}
HTTP 200
