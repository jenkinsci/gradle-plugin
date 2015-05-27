(function() {
	// created on demand
	var outline = null;
	var loading = false;

	var queue = []; // gradle tasks are queued up until we load outline.

	function loadOutline() {
		if (outline != null)
			return false; // already loaded

		if (!loading) {
			loading = true;
			var u = new Ajax.Updater(
					document.getElementById("side-panel"),
					rootURL
							+ "/descriptor/hudson.plugins.gradle.GradleTaskNote/outline",
					{
						insertion : Insertion.Bottom,
						onComplete : function() {
							if (!u.success())
								return; // we can't us onSuccess because
							// that kicks in before onComplete
							outline = document
									.getElementById("console-outline-body");
							loading = false;
							queue.each(handle);
							handleDone();
						}
					});
		}
		return true;
	}

	var type = '';
	var subs = [];
	var count = 0;

	function getMyDiv(name) {
		var myDiv = '';
		if (name.indexOf(':') < 0) {
			var id = "gradle-task-" + (iota++);
			myDiv = "<a href='#" + id
					+ "' style='left:10px;position:relative'>" + name
					+ "</a><br/>";
		} else {
			var tokens = name.split(':');
			myDiv = myDiv
					.concat("<details style='left:10px;position:relative'><summary>"
							+ name.substring(0, name.indexOf(':'))
							+ "</summary>");
			myDiv = myDiv.concat(getMyDiv(name.substring(name.indexOf(':') + 1,
					name.length - 1)));
			myDiv = myDiv.concat('</details>');
			// myDiv = myDiv.concat('</details>');
		}
		return myDiv;
	}

	function getMyDivL1(subs, type1) {
		var myDiv = "<details style='left:10px;position:relative'><summary>"
				+ type1 + "</summary>";
		var last = null;
		var i = 0;
		var entries = [];
		for (i = 0; i < subs.length; i++) {
			if (subs[i].indexOf(':') > -1) {
				var name = subs[i];
				var temp = name.substring(0, subs[i].indexOf(':'))

				if (last == temp) {
					entries.push(name.substring(name.indexOf(':') + 1,
							name.length));
				} else {

					if (entries.length > 0) {
						myDiv = myDiv.concat(getMyDivL2(entries, last));
					}
					entries = [];
					last = temp;
				}

			} else
				myDiv = myDiv.concat(getMyDiv(subs[i]))
		}

		if (entries.length > 0) {
			myDiv = myDiv.concat(getMyDivL2(entries, last));
		}

		myDiv = myDiv.concat('</details>');
		return myDiv;
	}

	function getMyDivL2(l2Param1, type2) {
		var myDiv = "<details style='left:10px;position:relative'><summary>"
				+ type2 + "</summary>";
		var last = null;
		var i = 0;
		var entries = [];
		for (j = 0; j < l2Param1.length; j++) {
			if (l2Param1[j].indexOf(':') > -1) {
				var name = l2Param1[j];
				var temp = name.substring(0, l2Param1[j].indexOf(':'))

				if (last == temp) {
					entries.push(name.substring(name.indexOf(':') + 1,
							name.length));
				} else {

					if (entries.length > 0) {
						myDiv = myDiv.concat(getMyDivL3(entries, last));
					}
					entries = [];
					last = temp;
				}

			} else
				myDiv = myDiv.concat(getMyDiv(l2Param1[j]))
		}

		if (entries.length > 0) {
			myDiv = myDiv.concat(getMyDivL3(entries, last));
		}

		myDiv = myDiv.concat('</details>');
		return myDiv;
	}

	function getMyDivL3(l3Param1, type3) {
		var myDiv = "<details style='left:10px;position:relative'><summary>"
				+ type3 + "</summary>";
		for (k = 0; k < l3Param1.length; k++) {
			myDiv = myDiv.concat(getMyDiv(l3Param1[k]))
		}
		myDiv = myDiv.concat('</details>');
		return myDiv;
	}

	function handle(e) {
		if (loadOutline()) {
			queue.push(e);
		} else {

			var tokens = e.innerHTML.split(":")
			if (type == tokens[0]) {

				if (tokens.length == 2)
					subs.push(tokens[1]);
				else {
					var append = '';
					for (i = 1; i < tokens.length; i++)
						append = append.concat(tokens[i] + ':');
					subs.push(append.substring(0, append.length - 1));
				}
			} else {
				if (type != '') {

					var myDiv = getMyDivL1(subs, type);
					outline.appendChild(parseHtml(myDiv));
				}
				type = tokens[0];
				subs = [];
				if (tokens.length == 2)
					subs.push(tokens[1]);
				else {
					var append = '';
					for (i = 1; i < tokens.length; i++)
						append = append.concat(tokens[i] + ':');
					subs.push(append.substring(0, append.length - 1));
				}

			}

			var id = "gradle-task-" + count++;
			if (document.all)
				e.innerHTML += '<a name="' + id + '"/>';
			else {
				var a = document.createElement("a");
				a.setAttribute("name", id);
				e.appendChild(a);
			}
		}
	}

	function handleDone() {
		var myDiv = getMyDivL1(subs, type);
		outline.appendChild(parseHtml(myDiv));
		subs = [];
	}

	Behaviour.register({
		// insert <a name="..."> for each Gradle task and put it into the
		// outline
		"b.gradle-task" : function(e) {
			handle(e);
		}
	});
}());
