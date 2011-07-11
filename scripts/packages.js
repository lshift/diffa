// Downloads the packages manifest and populates the links accordingly.

var base = "https://s3.amazonaws.com/diffa-packages/";

function packages(items) {
	var latest = items[0].latest;
	$('#zip').append($('<a href="' + base +'diffa-b' + latest + '.zip">Standalone Package</a>'));	
	$('#war').append($('<a href="' + base +'diffa-agent-b' + latest + '.war">WAR Archive</a>'));
}

$(document).ready(function() {	
	$.getJSON(base + "packages.js?callback=?",	function(data){});
});