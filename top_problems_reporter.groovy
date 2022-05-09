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
		summaryBeautificationRegexp = "Zabbix.* - |Prometheus |test|prod|preprod|dev|Prometheus: |" //to remove excessive data from Alert name before creating Problem
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
def linesCount = 0

//just...just don't ask, ok?
def String emptyLine = """

"""
def String newLine = """
""" 
//end of strange code

def makeComment (Issue issueToComment, String commentText, com.atlassian.jira.user.ApplicationUser commentUser, String vis) {  //at this point I'm not sure why do I need this method. Probably only because it looks more humanfriendly in code
	ComponentAccessor.commentManager.create(issueToComment, commentUser, commentText, null,ComponentAccessor.getComponent(ProjectRoleManager).getProjectRole("${vis}").getId(), true) //comment this line if you don't need visibility functionality.
}


//Отчет по открытым проблемам
def queryAllOpenProblems = "type in (Problem,\"Service request\") AND status not in (Closed, Cancelled, Contract) AND project = ${projectName} ORDER BY created DESC"
def resultsAllOpenProblems = searchService.search(user, jqlQueryParser.parseQuery(queryAllOpenProblems), pager)


def commentBody = """Список Problem с наибольшим числом инцидентов:

"""
def openProblemsFound = []
resultsAllOpenProblems.getResults().each {result ->;
	def currentIssueComment
    def printedSummary=result.summary.replaceAll(summaryBeautificationRegexp, "") //this is just for beautification. Can make a switch-case to set it for different projects.
    def queryAllLinked = "issuefunction in hasLinks(\"causes\") and issue in linkedIssues(${result.key})"
    def resultsAllLinked = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinked), pager)
    def linked=resultsAllLinked.total
    def linkedURL="[${resultsAllLinked.total}|${jqlToUrl(queryAllLinked)}]"
    if (linked > 0) {
	    def currentProblem = [result.key,printedSummary,linked,linkedURL]
        openProblemsFound.add(currentProblem)
    }
}


openProblemsFound = openProblemsFound.sort{ a,b -> a[2] <=> b[2] }.reverse()


for(problem in openProblemsFound) {
    linesCount+=1
    commentBody += """${problem[0]} ${problem[1]} ${problem[3]}"""+newLine
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
//Конец отчета по открытым проблемам

//Отчет по закрытым проблемам за полгода
def queryAllClosedProblems = "project = ${projectName} AND type in (Problem,\"Service request\") AND status in (Closed, Cancelled, Contract) AND created>-180d AND issueLinkType in (\"is caused by\")  ORDER BY created DESC"
def resultsAllClosedProblems = searchService.search(user, jqlQueryParser.parseQuery(queryAllClosedProblems), pager)

commentBody = """Список закрытых Problem за полгода с наибольшим числом инцидентов:

"""
def closedProblemsFound = []
resultsAllClosedProblems.getResults().each {result ->;
	def currentIssueComment
    def printedSummary=result.summary.replaceAll(summaryBeautificationRegexp, "") //this is just for beautification. Can make a switch-case to set it for different projects.
    def queryAllLinked = "issuefunction in hasLinks(\"causes\") and issue in linkedIssues(${result.key})"
    def resultsAllLinked = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinked), pager)
    def linked=resultsAllLinked.total
    def linkedURL="[${resultsAllLinked.total}|${jqlToUrl(queryAllLinked)}]"
	def currentProblem = [result.key,printedSummary,linked,linkedURL]
    closedProblemsFound.add(currentProblem)
}


closedProblemsFound = closedProblemsFound.sort{ a,b -> a[2] <=> b[2] }.reverse()

for(problem in closedProblemsFound) {
    linesCount+=1
    commentBody += """${problem[0]} ${problem[1]} ${problem[3]}"""+newLine
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
//Конец отчета по закрытым проблемам за полгода


//Отчет по повторяющимся проблемам
def queryAllProblems = "project = ${projectName} AND type in (Problem,\"Service request\") AND issueLinkType in (\"is caused by\") AND created > \"2021-07-01\"  ORDER BY created DESC"
def resultsAllProblems = searchService.search(user, jqlQueryParser.parseQuery(queryAllProblems), pager)

commentBody = """Список повторяющихся проблем на проекте:

"""
def allSummaries = []
def allProblems = []
resultsAllProblems.getResults().each {result ->;
    def printedSummary=result.summary.replaceAll(summaryBeautificationRegexp, "").replaceAll("\\[RPTISSUE\\] ", "").replaceAll("\\(|\\)|\\[|\\]", "").replaceAll("\"|	", "").replaceAll("  |   |    |  ", " ").trim().replaceAll(" +", " ") //this is just for beautification. Can make a switch-case to set it for different projects.
    allSummaries.add(printedSummary)
}

allSummaries = allSummaries.unique(false)

for (summary in allSummaries) {
    def queryAllLinked = "project = ${projectName} and summary ~ \"${summary}\" AND type in (Problem,\"Service request\") AND issueLinkType in (\"is caused by\") ORDER BY created DESC"
    def resultsAllLinked = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinked), pager)
    def linked=resultsAllLinked.total
    def linkedURL="[${resultsAllLinked.total}|${jqlToUrl(queryAllLinked)}]"
    if (linked > 1) {
        def currentProblem = [summary,linked,linkedURL]
        allProblems.add(currentProblem)
    }
}


allProblems = allProblems.sort{ a,b -> a[1] <=> b[1] }.reverse()

for(problem in allProblems) {
    linesCount+=1
    commentBody += """${problem[0]} ${problem[2]}"""+newLine
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
//Конец отчета по повторяющимся проблемам

//Отчет по кандидатам на закрытие
queryAllOpenProblems = "type in (Problem) AND status not in (Closed, Cancelled, Contract) AND project = ${projectName} and created < -7d ORDER BY created DESC"
resultsAllOpenProblems = searchService.search(user, jqlQueryParser.parseQuery(queryAllOpenProblems), pager)


commentBody = """Список Problem кандидатов на закрытие:

"""
def closeCandidates = []
def currentTime = new Date().getTime()
resultsAllOpenProblems.getResults().each {result ->;
	def currentIssueComment
    def queryAllLinked = "issuefunction in hasLinks(\"causes\") and issue in linkedIssues(${result.key}) order by created desc"
    def resultsAllLinked = searchService.search(user, jqlQueryParser.parseQuery(queryAllLinked), pager)
    def linked=resultsAllLinked.total
    def summarizingMessage=" "
    if (linked > 0) {
        def timeSinceLastAlert = ((currentTime - resultsAllLinked.results.get(0).created.getTime())/1000/60/60/24).intValue()
    	def linkedURL="[${resultsAllLinked.total}|${jqlToUrl(queryAllLinked)}]"
		def printedSummary=result.summary.replaceAll(summaryBeautificationRegexp, "") //this is just for beautification. Can make a switch-case to set it for different projects.
    	if (linked == 3 || timeSinceLastAlert >= 7) {
            if (linked == 3) {
                summarizingMessage+="| в течение 7 дней после создания Problem не было новых инцидентов"
            }
			if (timeSinceLastAlert >= 7) {
                summarizingMessage+="| новых инцидентов не было в течение ${timeSinceLastAlert} дней"
            }
            def currentProblem = [result.key,printedSummary,linked,linkedURL,summarizingMessage]
    		closeCandidates.add(currentProblem)
    	}
    }
}


closeCandidates = closeCandidates.sort{ a,b -> a[2] <=> b[2] }.reverse()

for(candidate in closeCandidates) {
    linesCount+=1
    commentBody += """ ${candidate[0]} ${candidate[1]} ${candidate[3]} ${candidate[4]}"""+newLine
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
//Конец отчета по кандидатам на закрытие