<!-- BEGIN HERMES GERMANY SECURITY.MD V0.0.1 BLOCK -->

## Security

We at Hermes Germany take the security of our software products and services seriously.

If you believe you have found a security vulnerability in any Hermes Germany-owned repository, please report it to us as described below.

## Reporting Security Issues

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them by (mailto:galapagos@hermesworld.com).

You should receive a response within 48 hours on Monday through Friday.

Please include the requested information listed below (as much as you can provide) to help us better understand the nature and scope of the possible issue:

  * Type of issue (e.g. buffer overflow, SQL injection, cross-site scripting, etc.)
  * Full paths of source file(s) related to the manifestation of the issue
  * The location of the affected source code (tag/branch/commit or direct URL)
  * Any special configuration required to reproduce the issue
  * Step-by-step instructions to reproduce the issue
  * Proof-of-concept or exploit code (if possible)
  * Impact of the issue, including how an attacker might exploit the issue

This information will help us triage your report more quickly.

## Preferred Languages

We prefer all communications to be in either German or English.

## Security Update policy

Security Updates will be published through GitHub. Currently we are not able to implement any other form of active communication to galapagos users.
We highly recommend watching the galapagos repository on GitHub for updates.

## Security Related Configuration

User authentication and authorization in Galapagos requires [keycloak](https://www.keycloak.org/).

## Know Securtiy Gaps

 * For non-productive environments Galapagos allows the generation of client certificates and private keys to create a ready-for-use .p12-file. When using this generation method, the private key will be transfered via network. The risk from this dependends greatly on your Galapagos setup and the sensitvity of your non-prod environments. That said, this is an intended convenience feature. Galapagos also offers client certificate generation by CSR, without transfering a private key, and enforces this method for production environments. 

<!-- END HERMES GERMANY SECURITY.MD BLOCK -->
