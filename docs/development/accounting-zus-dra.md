# ZUS DRA export boundary

Investory's ZUS DRA implementation is deliberately split into four boundaries:

1. `ZusDraDeclaration` is the unsigned filing model. Its numbered lists preserve the field order
   used by the ZUS DRA sections; they are not the accounting calculation model.
2. `ZusDraValidator` checks the local contract before any XML is produced.
3. `ZusDraKeduRenderer` creates an unsigned KEDU XML package containing one `ZUSDRA` document.
4. `ZusDraKeduXmlValidator` validates the package against the bundled ZUS KEDU 5.7 schema.

This slice does not sign or submit documents. It is suitable for a review-and-export flow and for
import into ePłatnik. Direct EWD submission remains a separate adapter because it needs a user-owned
signature operation, SOAP transport, retry/idempotency handling, confirmation polling, and durable
UPO/authority evidence.

`ZusDraSubmissionClient` is the seam for that future adapter. `MockZusDraSubmissionClient` is the
current implementation: it performs the read, render, and schema-validation path, then stores
`submission.kedu.xml` plus `submission.properties` under a caller-provided directory. Reading
returns the stored payload and metadata. Its status is `MOCK_STORED`; it must never be presented as
submitted, accepted, or proof of a ZUS receipt.

`RealZusDraConfirmationClient` performs the read-only EWD `PobierzPotwierdzenie` call. It requires
the ZUS endpoint and a user-owned PKCS12 client certificate. `FileZusDraConfirmationStore` can
persist the returned package, raw SOAP response, hash, task identifier, and timestamps. The PT2
test endpoint is `https://www.pt2.zus.pl/SDWI_AWS2/NawsUslugi.svc`; production endpoint details
must come from the ZUS interface documentation and must not be guessed.

The reference fixture is `modules/accounting/src/test/resources/fixtures/zus/Deklaracja rozliczeniowa
ZUS DRA 01 10-2025.pdf`. The test reads the PDF as a reference document and checks the visible
October 2025 values before checking the generated KEDU structure. The fixture is evidence for the
field mapping, not a submission payload.

The schema is downloaded from the current ZUS interface-documentation package. It is kept in the
repository so validation is deterministic and does not depend on network availability during a build.
When ZUS publishes a new KEDU schema, add a new versioned resource and renderer/validator contract;
do not silently replace the schema used by an existing artifact.
