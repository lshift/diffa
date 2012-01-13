Enabling External Authentication
================================

Diffa currently supports External Authentication via ActiveDirectory and LDAP.


Enabling Active Directory Authentication
----------------------------------------

AD support is enabled through two system configuration options. Assuming you have an active directory with
the domain "mydomain.com", controlled by the Domain Controller dc.mydomain.com, you'd configure the following
Diffa options:

* activedirectory.domain -> mydomain.com
* activedirectory.server -> ldap://dc.mydomain.com

This can be configured via the REST API as such (assuming a Diffa instance running on http://localhost:19093/diffa-agent
with the default user still in place):

curl -u guest:guest -XPOST -d"activedirectory.domain=mydomain.com&activedirectory.server=ldap://dc.mydomain.com" \
  -H"Content-Type: application/x-www-form-urlencoded" http://localhost:19093/diffa-agent/rest/root/system/config

If settings are applied successfully, the server log should include a line such as:

  INFO ExternalAuthenticationProviderSwitch:43 - Using ActiveDirectory authentication for domain mydomain.com with server ldap://dc.mydomain.com

To disable Active Directory integration, remove the configuration options:

curl -u guest:guest -XDELETE http://localhost:19093/diffa-agent/rest/root/system/config/activedirectory.domain
curl -u guest:guest -XDELETE http://localhost:19093/diffa-agent/rest/root/system/config/activedirectory.server


Enabling LDAP Authentication
----------------------------

For non-Active Directory LDAP implementations, another set of options are provided. Note that you should must disable
Active Directory integration before attempting to configure LDAP authentication.

Basic LDAP integration is enabled by setting the property `ldap.url`. This property should look something like
`ldap://server.mydomain.com/dc=mydomain,dc=com`. Based upon the structure of your LDAP system, there are a number
of other options that may need modifying:

* `ldap.userdn.pattern` - if all of your users follow a specific DN pattern, then this property should be specified.
  For example, specifying `uid={0},ou=People` would result in the User DN for `someuser` being `uid=someuser,ou=People`.
  For this property, {0} will be substituted with the provided username. Note that this property should not repeat
  your domain DN.
* `ldap.user.search.filter` - if users should be searched for, this property specifies the filter that should be used.
  For example, specifying `(uid={0})` will search for users with a `uid` attribute matching the login name.
* `ldap.user.search.base` - narrows the search specified by `ldap.user.search.filter` to within a specific structure within
  your directory. For example, specifying `(ou=People)` would result in the search only occuring within the People subtree.
* `ldap.group.search.base` - specifies that groups should be searched for within the given root. If this property is not
  configured, then groups will not be searched for the user. For example, setting this property to `ou=groups` would search
  within the `groups` subtree for all users with a `member` property matching the user DN. See `ldap.group.search.filter` for
  modifying the attributes used.
* `ldap.group.search.filter` - specifies that groups should be filtered based upon the given filter, instead of the default
  if `(member={0})` (which searches for groups based upon an attribute specifying the full user DN). For example, to search
  for groups containing an attribute specifying the username as a memberUid attribute, the filter `(memberUid={1})` would be specified.
  For this property, {0} will be substituted with the full user DN, and {1} will be substituted with the username.
* `ldap.group.role.attribute` - specifies the attribute to use as the group name for discovered groups. Defaults to `cn`.
  For example, to use the `ou` property as the group name (see Privilege Management section of this documentation for the
  importance of group names), then this property would be set to `ou`.

These properties can be configured via the REST API as such (assuming a Diffa instance running on http://localhost:19093/diffa-agent
with the default user still in place):

curl -u guest:guest -XPOST -d"ldap.url=ldap://server.mydomain.com/dc=mydomain,dc=com&ldap.userdn.pattern=uid={0},ou=People" \
  -H"Content-Type: application/x-www-form-urlencoded" http://localhost:19093/diffa-agent/rest/root/system/config

If settings are applied successfully, the server log should include a line such as:

  INFO ExternalAuthenticationProviderSwitch:43 - Using LDAP authentication with server ldap://server.mydomain.com/dc=mydomain,dc=com, with User DN pattern 'uid={0},ou=People'

To disable LDAP integration, remove all ldap.* configuration options, for example:

curl -u guest:guest -XDELETE http://localhost:19093/diffa-agent/rest/root/system/config/ldap.url
curl -u guest:guest -XDELETE http://localhost:19093/diffa-agent/rest/root/system/config/ldap.userdn.pattern
(etc)


Privilege Management
--------------------

All users authenticated externally are automatically granted "user" privileges. User privileges simply allow
the user to load the Diffa User Interface, but not access any data nor administration functions. Privileges can be assigned
to users by creating internal accounts matching their username, or matching the names of the user's assigned groups.

For instance, if you had a user 'frank.diffman' with the groups 'Development' and 'Support'. If you wanted to make this
user a root system user, then you'd create a Diffa user (with a dummy password) marked as a superuser. For example:

  curl -u guest:guest -XPOST -d'{"name":"frank.diffman","email":"frank.diffman@example.com","superuser":true,"external":true}' -H"Content-Type: application/json" http://localhost:19093/diffa-agent/rest/security/users

Note that the above command specifies that the user is external. This allows the user to be defined without a password,
ensuring that the only way they can log in is with external acceptance. Specifying a password instead would allow
the user to log in with either their external password or the stored Diffa password.

If you wanted to allow all Support users to log into the 'staging' Diffa domain, then you'd create a user such as:

  curl -u guest:guest -XPOST -d'{"name":"Support","email":"support@example.com","superuser":false,"external":true}' -H"Content-Type: application/json" http://localhost:19093/diffa-agent/rest/security/users

Then assign it to the domain 'staging':

  curl -u guest:guest -XPOST http://localhost:19093/diffa-agent/rest/staging/config/members/Support


Debugging
---------

If login isn't behaving as expected, the AD information being provided to Diffa can be diagnosed by enabling DEBUG logging
on org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider. The output will
detail users that are attempting to login, and group information being returned by the directory about the user.

LDAP information being provided to Diffa can be diagnosed by enabling DEBUG logging on the following classes:

* org.springframework.security.ldap.authentication.BindAuthenticator
* org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator