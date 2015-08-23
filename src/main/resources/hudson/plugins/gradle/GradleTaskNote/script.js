(function() {
    // created on demand
    var outline = null;
    var loading = false;

    var queue = []; // gradle tasks are queued up until we load outline.

    function loadOutline() {
        if (outline != null)  return false;   // already loaded

        if (!loading) {
            loading = true;
            var u = new Ajax.Updater(document.getElementById("side-panel"),
                    rootURL + "/descriptor/hudson.plugins.gradle.GradleTaskNote/outline",
            {insertion: Insertion.Bottom, onComplete: function() {
                if (!u.success())   return; // we can't us onSuccess because that kicks in before onComplete
                outline = document.getElementById("console-outline-body")
                			.getElementsByTagName('ul')[0];
                loading = false;
                queue.each(handle);
            }});
        }
        return true;
    }

    function handle(e) {
        if (loadOutline()) {
            queue.push(e);
        } else {
            var id = "gradle-task-" + (iota++);
            outline.appendChild(parseHtml("<li><a href='#" + id + "'>" + e.innerHTML + "</a></li>"));

            if (document.all)
                e.innerHTML += '<a name="' + id + '"/>';  // IE8 loses "name" attr in appendChild
            else {
                var a = document.createElement("a");
                a.setAttribute("name", id);
                e.appendChild(a);
            }
        }
    }

    Behaviour.register({
        // insert <a name="..."> for each Gradle task and put it into the outline
        "b.gradle-task" : function(e) {
            handle(e);
        }
    });
}());
