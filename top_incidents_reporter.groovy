import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.issue.link.IssueLinkTypeManager
import com.atlassian.jira.security.roles.ProjectRoleManager
import java.sql.Timestamp
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.context.IssueContext
import com.atlassian.jira.issue.context.IssueContextImpl
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.LinkCollectionImpl
import com.atlassian.jira.issue.fields.config.manager.PrioritySchemeManager


def jqlToUrl (String jql) {
	def UrlizedJql = "${com.atlassian.jira.component.ComponentAccessor.getApplicationProperties().getString("jira.baseurl")}/issues/?jql=" + jql.replaceAll(/ /, "%20").replaceAll(/=/, "%3D").replaceAll("\"", "%22").replaceAll(",", "%2C")
	return UrlizedJql
}

/*
currentIssue definition is just type casting for further usage. 
'issue.key' here and 'issue' itself is the context constant passed from rule execution. 
It's handy and can be used anywhere (even inside the methods), but the code becomes not flexible, and can't be easily debugged in console.
*/
def currentIssue = ComponentAccessor.getIssueManager().getIssueObject(issue.key)  // you can replace issue.key with some actual issue key to use the script in console, e.g. <...>.getIssueObject("PRJ-78") 
def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
def searchService = ComponentAccessor.getComponent(SearchService.class)
def pager = PagerFilter.getUnlimitedFilter()
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def projectName = "${currentIssue.key}".replaceAll(/-.*/, "")
def visibility = "Staff only" //Name of the visibility group for comment. Default is for Project Roles name. Should be replaced with proper. It may be not useful for someone. You can just comment it out. Also you need to change makeComment function. 
def summaryBeautificationRegexp = "Priority.*\\|" 
def removeExcessiveData
def linesCount = 0

switch (projectName) {
	case "PRJ1":
		summaryBeautificationRegexp = "Zabbix.* - |Prometheus |test|prod|preprod|dev|Prometheus: |" //to remove excessive data from Alert name
		removeExcessiveData = "Prometheus [a-zA-Z]* | on [0-9A-Za-z\\-]+\$ |Zabbix [a-zA-Z]* |prod | prod |test | test |preprod | preprod | on [0-9\\.A-Za-z\\-]+\$| on  [0-9\\.A-Za-z\\-]+\$| on [A-Z\\.a-z0-9\\-]* " //to remove all data from summary, except the name of monitor
	break
	case "PRJ2":
	break
	case "PRJ3":
	break
	default:
	break
}

def created
def resolved
def timeInRed


//just...just don't ask, ok?
def String emptyLine = """

"""
def String newLine = """
""" 
//end of strange code

def makeComment (Issue issueToComment, String commentText, com.atlassian.jira.user.ApplicationUser commentUser, String vis) {  //at this point I'm not sure why do I need this method. Probably only because it looks more humanfriendly in code
	ComponentAccessor.commentManager.create(issueToComment, commentUser, commentText, null,ComponentAccessor.getComponent(ProjectRoleManager).getProjectRole("${vis}").getId(), true) //comment this line if you don't need visibility functionality.
}


//Отчет по повторяющимся инцидентам
def queryAllIncidentsLastMonth = "project = ${projectName} AND type in (Incident) AND created > -30d  ORDER BY created DESC"
def resultsAllIncidentsLastMonth = searchService.search(user, jqlQueryParser.parseQuery(queryAllIncidentsLastMonth), pager)

def commentBody = """Список повторяющихся инцидентов на проекте за 30 дней:

"""
def allSummaries = []
def AllIncidentsLastMonth = []
resultsAllIncidentsLastMonth.getResults().each {result ->;
    def printedSummary=result.summary.replaceAll(removeExcessiveData, "").replaceAll(summaryBeautificationRegexp, "").replaceAll("\\[RPTISSUE\\] ", "").replaceAll("\\(|\\)|\\[|\\]", "").replaceAll("\"|	", "").replaceAll("  |   |    |  ", " ").trim().replaceAll(" +", " ") //this is just for beautification. Can make a switch-case to set it for different projects.
    allSummaries.add(printedSummary)
}

allSummaries = allSummaries.unique(false)

for (summary in allSummaries) {
    def queryAllLinked = "project = ${projectName} and summary ~ \"${summary}\" AND type in (Incident) AND created > -30d ORDER BY created DESC"
    def resultsAllLinked = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinked), pager)
    def total=resultsAllLinked.total
    def linkedURL="[${resultsAllLinked.total}|${jqlToUrl(queryAllLinked)}]"
    if (total > 1) {
        def currentIncident = [summary,total,linkedURL]
        AllIncidentsLastMonth.add(currentIncident)
    }
}


AllIncidentsLastMonth = AllIncidentsLastMonth.sort{ a,b -> a[1] <=> b[1] }.reverse()

for(incident in AllIncidentsLastMonth) {
	linesCount+=1
    commentBody += """${incident[0]} ${incident[2]}"""+newLine
	if (linesCount == 20) {
        linesCount = 0
        break
    }
}


if (commentBody.length() > 0 && commentBody.length() < 32767) {
	makeComment(currentIssue, commentBody, user, visibility)
}
else {
    addError("Comment is empty.")
}
//Конец отчета по повторяющимся инцидентам