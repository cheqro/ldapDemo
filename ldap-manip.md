# LDAP --- Ajouter un membre à un groupe et inspecter les rôles dans Spring Boot

## 1. Objectif

Ce guide montre comment ajouter un utilisateur à un groupe OpenLDAP,
vérifier l'appartenance au groupe, puis inspecter les rôles récupérés
par Spring Security.

Exemple local : - LDAP : `ldap://localhost:389` - Base DN :
`dc=company,dc=com` - Utilisateurs : `ou=users,dc=company,dc=com` -
Groupes : `ou=groups,dc=company,dc=com` - Application Spring Boot :
`http://localhost:8081`

> Les mots de passe utilisés ici sont réservés au laboratoire local.

## 2. Ajouter Karim au groupe APP_USERS

### Créer `add-karim-to-app-users.ldif`

``` ldif
dn: cn=APP_USERS,ou=groups,dc=company,dc=com
changetype: modify
add: member
member: uid=karim,ou=users,dc=company,dc=com
```

Explication : - `dn` désigne l'entrée LDAP à modifier. -
`changetype: modify` demande une modification de l'entrée existante. -
`add: member` ajoute une valeur à l'attribut `member`. - La dernière
ligne est le DN complet de Karim.

Cette modification conserve les membres déjà présents, par exemple Sara.

### Appliquer la modification

Dans le dossier contenant le fichier :

``` bash
ldapmodify -x \
  -H ldap://localhost:389 \
  -D "cn=admin,dc=company,dc=com" \
  -w admin123 \
  -f add-karim-to-app-users.ldif
```

Résultat attendu :

``` text
modifying entry "cn=APP_USERS,ou=groups,dc=company,dc=com"
```

Erreurs courantes : - `No such object (32)` : le groupe n'existe pas ou
le DN est incorrect. - `Type or value exists` : Karim est probablement
déjà membre du groupe.

## 3. Vérifier les membres du groupe

``` bash
ldapsearch -x \
  -H ldap://localhost:389 \
  -D "cn=admin,dc=company,dc=com" \
  -w admin123 \
  -b "cn=APP_USERS,ou=groups,dc=company,dc=com" \
  "(objectClass=groupOfNames)" cn member
```

Le résultat doit contenir les deux valeurs `member` :

``` ldif
dn: cn=APP_USERS,ou=groups,dc=company,dc=com
cn: APP_USERS
member: uid=sara,ou=users,dc=company,dc=com
member: uid=karim,ou=users,dc=company,dc=com
```

L'ordre des membres peut varier.

## 4. Ajouter un utilisateur à un autre groupe

Exemple : ajouter Sara à `APP_ADMINS`. Crée
`add-sara-to-app-admins.ldif` :

``` ldif
dn: cn=APP_ADMINS,ou=groups,dc=company,dc=com
changetype: modify
add: member
member: uid=sara,ou=users,dc=company,dc=com
```

Puis exécute :

``` bash
ldapmodify -x \
  -H ldap://localhost:389 \
  -D "cn=admin,dc=company,dc=com" \
  -w admin123 \
  -f add-sara-to-app-admins.ldif
```

Le groupe doit déjà exister. Un groupe `groupOfNames` doit avoir au
moins un membre.

## 5. Comment Spring Security convertit les groupes en rôles

Configuration type :

``` java
DefaultLdapAuthoritiesPopulator authoritiesPopulator =
    new DefaultLdapAuthoritiesPopulator(contextSource, "ou=groups");

authoritiesPopulator.setGroupSearchFilter("(member={0})");
authoritiesPopulator.setGroupRoleAttribute("cn");
authoritiesPopulator.setRolePrefix("ROLE_");
authoritiesPopulator.setConvertToUpperCase(false);
```

Fonctionnement : 1. Spring authentifie l'utilisateur dans LDAP. 2. Il
recherche les groupes sous `ou=groups` dont l'attribut `member` contient
le DN de cet utilisateur. 3. Il lit l'attribut `cn` du groupe. 4. Avec
le préfixe `ROLE_`, `APP_USERS` devient `ROLE_APP_USERS` et `APP_ADMINS`
devient `ROLE_APP_ADMINS`. 5. Les règles d'autorisation utilisent ces
autorités.

Exemple de règles :

``` java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/public").permitAll()
    .requestMatchers("/admin").hasRole("APP_ADMINS")
    .requestMatchers("/user").hasAnyRole("APP_ADMINS", "APP_USERS")
    .anyRequest().authenticated())
```

`hasRole("APP_USERS")` vérifie normalement l'autorité `ROLE_APP_USERS`.
Ne passe pas le préfixe `ROLE_` à `hasRole`.

## 6. Inspecter les rôles depuis Spring Boot

Pour le laboratoire, ajoute `AuthDebugController.java` dans le package
de ton application :

``` java
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthDebugController {

    @GetMapping("/debug/auth")
    public Map<String, Object> debug(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toList());

        return Map.of(
            "username", authentication.getName(),
            "authorities", authorities
        );
    }
}
```

Redémarre l'application si nécessaire, puis appelle :

``` bash
curl -i -u 'karim:Karim123!' \
  http://localhost:8081/debug/auth
```

Si Karim appartient à `APP_ADMINS` et `APP_USERS`, la réponse doit
contenir ces deux autorités :

``` json
{
  "username": "karim",
  "authorities": [
    "ROLE_APP_ADMINS",
    "ROLE_APP_USERS"
  ]
}
```

L'ordre peut varier. Si tu obtiens `401`, vérifie les identifiants et la
configuration LDAP. Si tu obtiens `403`, vérifie les règles d'accès et
l'existence de l'endpoint.

## 7. Tester les autorisations

``` bash
curl -i -u 'karim:Karim123!' http://localhost:8081/user
curl -i -u 'karim:Karim123!' http://localhost:8081/admin
```

Avec les groupes indiqués, Karim doit pouvoir accéder aux deux
endpoints, à condition que les contrôleurs et les règles correspondent à
ces chemins.

Pour Sara, si elle appartient uniquement à `APP_USERS` :

``` bash
curl -i -u 'sara:Sara123!' http://localhost:8081/user
curl -i -u 'sara:Sara123!' http://localhost:8081/admin
```

`/user` doit être autorisé et `/admin` doit normalement répondre
`403 Forbidden`.

## 8. Points de débogage importants

-   La méthode qui construit `SecurityFilterChain` s'exécute au
    démarrage, pas à chaque requête.
-   Les filtres de la chaîne s'exécutent pour les requêtes entrantes.
-   Avec HTTP Basic, Spring réauthentifie généralement les identifiants
    à chaque requête.
-   Après une modification LDAP, lance une nouvelle requête pour
    vérifier les autorités actualisées.
-   Vérifie d'abord LDAP avec `ldapsearch`, puis Spring avec
    `/debug/auth`.
-   `/debug/auth` expose des informations de sécurité : garde-le pour le
    développement et retire-le ou protège-le avant la production.

## 9. Récapitulatif

  ----------------------------------------------------------------------------------------------------------------------------------------------
  Besoin                              Commande
  ----------------------------------- ----------------------------------------------------------------------------------------------------------
  Ajouter un membre                   `ldapmodify -x ... -f add-karim-to-app-users.ldif`

  Vérifier les membres LDAP           `ldapsearch -x ... -b "cn=APP_USERS,ou=groups,dc=company,dc=com" "(objectClass=groupOfNames)" cn member`

  Inspecter les rôles Spring          `curl -i -u 'karim:Karim123!' http://localhost:8081/debug/auth`

  Tester l'accès utilisateur          `curl -i -u 'karim:Karim123!' http://localhost:8081/user`

  Tester l'accès admin                `curl -i -u 'karim:Karim123!' http://localhost:8081/admin`
  ----------------------------------------------------------------------------------------------------------------------------------------------
