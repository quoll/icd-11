# ICD-11 Extractor
Turtle export of ICD-11.

## Description
This project walks the [ICD-11](https://icd.who.int/en/) tree for both entities and linearizations, retrieving each entity, and merging them into an RDF graph.
The graph is then output in [TTL](https://www.w3.org/TR/turtle/) format.

Data is retrieved via the [ICD-11 HTTP API](https://icd.who.int/icdapi) from either the public endpoint provided by the [World Health Organization (WHO)](https://www.who.int/standards/classifications/classification-of-diseases) or from a local installation (available from the [ICD-11 API page](https://icd.who.int/icdapi) under "Deploying the ICD-API Locally").

Retrieving data from the WHO public endpoint requires a free account with the WHO, which in turn requires agreement with the [WHO licensing terms](https://icd.who.int/en/docs/icd11-license.pdf) (Creative Commons Attribution-NoDerivs 3.0 IGO license (CC BY-ND 3.0 IGO)).

From a practical perspective, while data can be retrieving from the public endpoint at the WHO, this is **not recommended**. This is because the API requires a separate HTTP request for each of the 71175 entities, and 36941 linearizations (as of 2026). Using a local installation is an order of magnitude faster, and does not stress the public servers. The local API (available as a Linux or Windows service, or as a service in a Docker container) also has the benefit of not requiring authentication.

## Motivation
ICD-11 is the first version of ICD to be defined in RDF, using a graph rather than a hierarchical structure. However, while the data is freely available, it is only accessible via API, one entity at a time. This makes traversing the graph awkward and slow.

Requests to see the data are met with responses of this being "impossible" due to the data not fitting a spreadsheet. This suggests that the staff at the WHO assume users to be unaware of graph data. Fortunately, the licensing conditions of ICD-11 allow the data to be extracted and the full graph to be built.

## Use
icd11-extract accepts the following command line options:

```
clj -M:main [-H host] [-p port] [-o out_file] [-L linearization_file]
            [-r release] [-l linearization] [-a auth_string]
            [-c client-id] [-s client-secret] [-h]

  -H <host>, --host <host>
      The server to access. If this is id.who.int then credentials must be available. Defaults to 'localhost'.

  -h, --help
      Prints this text and exits.

  -p <port>, --port <port>
      The port to use. This defaults to 6382 for all hosts except icd.who.int, in which case it will be 443.

  -o <out_file>, --outfile <out_file>
      The file to write the foundation entity graph to. Defaults to 'icd11.ttl'.

  -L <linearization_file>, --linear <linearization_file>
      The file to write the linearization graph to. Defaults to <unsuffixed out_file>'-linear.ttl'

  -r <release>
      Release version. Defaults to '2025-01'.

  -l <linearization>, --linearization <linearization>
      The linearization name. Defaults to 'mms' (Mortality and Morbidity Statistics).

  -a <auth>, -auth <auth>
      An authorization token. This is for id.who.int and can be obtained from the OAUTH2 service:
        https://icdaccessmanagement.who.int/connect/token
      It can also be accesed after logging into the Swagger page at:
        https://id.who.int/swagger/index.html
      Not required when a client-id and client-secret are provided.

  -c <client_id>, --client <client_id>
      A client-id value to use for authentication to id.who.int. Overrides the config file.

  -s <client_secret>, --secret <client_secret>
      A client-secret value to use to authentication to id.who.int. Overrides the confile file.


  Client credentials for id.who.int are provided after creating an account at:
    https://icd.who.int/icdapi
  Once an account has been created, a client-id/client-secret pair can be created at:
    https://icd.who.int/icdapi/Account/AccessKey

  These values can be provided on the command line, or in a file named '.icd'
  in the user's home directory. The format of this file is:

client-id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
client-secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx=

  The credentials may be overriden using the environment vars ICD_ID and ICD_SECRET.
```
Using `clj -X:xmain` is also supported if you prefer to preconfigure this in a `.edn` file.

## License
Copyright © 2026 Paula Gearon

Distributed under the Eclipse Public License version 2.0.
