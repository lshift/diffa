Enabling External Authentication
================================

Diffa currently supports External Authentication via ActiveDirectory. This is enabled through two system
configuration options. Assuming you have an active directory with the domain "mydomain.com", controlled by the
Domain Controller dc.mydomain.com, you'd configure the following Diffa options:

* activedirectory.domain -> mydomain.com
* activedirectory.server -> ldap://dc.mydomain.com

This can be configured via the REST API as such (assuming a Diffa instance running on http://localhost:19093/diffa-agent
with the default user still in place):

curl -u guest:guest -XPUT -d"mydomain.com" -H"Content-Type: text/plain" http://localhost:19093/diffa-agent/rest/root/system/config/activedirectory.domain
curl -u guest:guest -XPUT -d"ldap://dc.mydomain.com" -H"Content-Type: text/plain" http://localhost:19093/diffa-agent/rest/root/system/config/activedirectory.server

IMPORTANT: Once these options have been applied, the Diffa server must be restarted. External Authentication options are
only applied on agent startup. If settings are applied successfully, the server log should include a line such as:

  INFO ExternalAuthenticationProviderSwitch:43 - Using ActiveDirectory authentication for domain mydomain.com with server ldap://dc.mydomain.com


Privilege Management
--------------------

All users within the Active Directory domain are automatically granted "user" privileges. User privileges simply allow
the user to load the Diffa User Interface, but not access any data nor administration functions. Privileges can be assigned
to users by creating internal accounts matching their username, or matching the names of the user's assigned groups.

For instance, if you had a user 'frank.diffman' with the groups 'Development' and 'Support'. If you wanted to make this
user a root system user, then you'd create a Diffa user (with a dummy password) marked as a superuser. For example:

  curl -u guest:guest -XPOST -d'{"name":"frank.diffman","email":"frank.diffman@example.com","superuser":true,"external":true}' -H"Content-Type: application/json" http://localhost:19093/diffa-agent/rest/security/users

Note that the above command specifies that the user is external. This allows the user to be defined without a password,
ensuring that the only way they can log in is with Active Directory acceptance. Specifying a password instead would allow
the user to log in with either their Active Directory password or the stored Diffa password.

If you wanted to allow all Support users to log into the 'staging' Diffa domain, then you'd create a user such as:

  curl -u guest:guest -XPOST -d'{"name":"Support","email":"support@example.com","superuser":false,"external":true}' -H"Content-Type: application/json" http://localhost:19093/diffa-agent/rest/security/users

Then assign it to the domain 'staging':

  curl -u guest:guest -XPOST http://localhost:19093/diffa-agent/rest/staging/config/members/Support


Debugging
---------

If login isn't behaving as expected, the AD information being provided to Diffa can be diagnosed by enabling DEBUG logging
on org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider. The output will
detail users that are attempting to login, and group information being returned by the directory about the user.