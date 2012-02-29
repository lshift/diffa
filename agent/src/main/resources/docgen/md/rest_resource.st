---
title: $resource.method$ $resource.nicePath$ | REST API Documentation
layout: default
---

<div id="menu" markdown="1">
$rest_menu()$
</div>

<div id="resources" markdown="1">
$resource.method$ $resource.nicePath$
=======================================================

<em>$resource.description$</em>

Entity Type
-----------
$resource.entityName$

URL
---
http://server:port/diffa-agent/$resource.nicePath$

$if(resource.mandatoryParameters)$ 
Mandatory Parameters
--------------------
$resource.mandatoryParameters: { p |

### $p.name$

*$p.datatype$*

$p.description$
}$
$endif$

$if(resource.optionalParameters)$ 
Optional Parameters
-------------------
$resource.optionalParameters: { p |

### $p.name$

*$p.datatype$*

$p.description$
}$
$endif$

$if(resource.example)$
Example
-------
$endif$
</div>
$if(resource.example)$
<div id="example">
<pre class="brush: js">$resource.example$</pre>
</div>
$endif$