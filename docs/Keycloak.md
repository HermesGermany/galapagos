# Keycloak Configuration for Galapagos

Galapagos currently **requires** a Keycloak as authentication & authorization software. The Keycloak configuration can
be specified in a separate file. This file must then be configured in the property `keycloak.configurationFile`, e.g.

```
keycloak.configurationFile=file:./keycloak-local.json
```

The Keycloak configuration is
documented [at Github](https://github.com/keycloak/keycloak-documentation/blob/master/securing_apps/topics/oidc/java/java-adapter-config.adoc)
.

The auto-generated Keycloak config file by the devsetup scripts looks similar to this:

```json
{
  "auth-server-url": "http://192.168.56.101:32101/auth",
  "realm": "galapagos",
  "resource": "galapagos-webapp-dev",
  "public-client": true,
  "use-resource-role-mappings": true,
  "principal-attribute": "preferred_username"
}
```

If you choose to use another Keycloak instance, you will have to adjust this configuration accordingly. Please note the
following **fix** requirements imposed by Galapagos:

* Each administrator user **must** have a Role named `admin` assigned
* Each standard user (including administrators) **must** have a Role named `user` assigned

You can e.g. use Keycloak's integrated identity provider and mapping functions to connect your company's Active
Directory and map users of one AD group to the `admin` role, and users of another AD group to the `user` role (these
roles will have to be defined in your realm if you do setup your own Realm).
