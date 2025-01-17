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

switch (projectName) {
	case "PRJ1":
		summaryBeautificationRegexp = "Zabbix.* - |Prometheus |test|prod|preprod|dev" //to remove excessive data from Alert name before creating Problem
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

def queryAllLinkedIncidents = "type = Incident AND project = ${projectName} AND issue in linkedIssues(${currentIssue.key}) and issueLinkType = causes ORDER BY created DESC"
def resultsAllLinkedIncidents = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinkedIncidents), pager)


def commentBody = """Список прилинкованных алертов:
{code:java}
"""
def commentAllLinkedIncidents=""

resultsAllLinkedIncidents.getResults().each {result ->;
	def currentIssueComment
	created = result.created
	resolved = result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time"))
    if (resolved == null) {
        timeInRed = "Still Red"
        resolved = "Not resolved"
    }
    else {
    	timeInRed = ((resolved.getTime() - created.getTime())/60/1000).intValue() //this line works. Console releases it without problems, however rules have some problems with it. Probably because of crazy type casting between two branches of 'if'.
    }
    def printedSummary=result.summary.replaceAll(summaryBeautificationRegexp, "") //this is just for beautification. Can make a switch-case to set it for different projects.
	currentIssueComment = """ ${result.key} - ${printedSummary} Created: ${created.toString().replaceAll(":.{2}[.].{1,3}", "")} Resolved: ${resolved.toString().replaceAll(":.{2}[.].{1,3}", "")} Minutes Red: ${timeInRed}"""+newLine
	if (commentAllLinkedIncidents.length() + currentIssueComment.length() + commentBody.length() > 32700) {
		makeComment(currentIssue, commentBody + commentAllLinkedIncidents+"{code}", user, visibility)
		commentBody = "{code:java}"
		commentAllLinkedIncidents = ""
	}
	commentAllLinkedIncidents += currentIssueComment
}
	commentBody += commentAllLinkedIncidents + """{code}"""+emptyLine

if (commentAllLinkedIncidents.length() > 0 && commentBody.length() < 32767) {
	makeComment(currentIssue, commentBody, user, visibility)
}
else {
    addError("Comment is empty, or exceed 32767 char limit. Ilya is smart enough to predict it might happen some time, but too lazy to write big comments dividing in several.")
}