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
def issueComponent = currentIssue.getComponents().head().name //getting only first component. BTW, script will fail if components are empty
def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
def searchService = ComponentAccessor.getComponent(SearchService.class)
def pager = PagerFilter.getUnlimitedFilter()
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
def projectName = "${currentIssue.key}".replaceAll(/-.*/, "")
def Boolean createIssueFlag = false //this one is to avoid creating multiple problems by this rule. 
def visibility = "Staff only" //Name of the visibility group for comment. Default is for Project Roles name. Should be replaced with proper. It may be not useful for someone. You can just comment it out. Also you need to change makeComment function. 

def Integer repeatableCheckGap = 15 //default. This set time gap in minutes for finding repeatable issues
def Integer thresholdRepeatable48h = 3 //default. If there were more than 3 issues (excluding current) with same summary and component created in last 48h  - trigger Problem creation
def Integer thresholdRepeatable7d = 5 //default. If there were more than 5 issues (excluding current) with same summary and component created in last 7 days - trigger Problem creation
def Integer thresholdRepeatableDaily = 3 //default. If there were 3 issues (the current one is 4th) found in findRepeatableIssues for 7 days before the current ticket - trigger Problem creation.
def Integer thresholdRepeatableWeekly = 2 //default.If there were 2 issues (the current one is 3rd) found in findRepeatableIssues for 5 weeks before the current ticket - trigger Problem creation.

def currentIssueSummary = currentIssue.summary.replaceAll("\\(|\\)|\\[|\\]", "") //use this one in JQLs, it has []() removed. Otherwise search may fail without any errors (when using functions in jql, like linkedIssuesOf) 
def summaryBeautificationRegexp = "" //default. If project requires some regexp to simplify the summary for related problem creation - it will be redefined later in the code, along with some thresholds.
def linkedIssuePriority = "Normal" //default. Priority of created Problems (of other is not redifined in rule)
//just...just don't ask, ok?
def String emptyLine = """

"""
def String newLine = """
""" 
//end of strange code

//defining some custom thresholds or variables specific to Project
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




def makeComment (Issue issueToComment, String commentText, com.atlassian.jira.user.ApplicationUser commentUser, String vis) {  //at this point I'm not sure why do I need this method. Probably only because it looks more humanfriendly in code
	//only one line should be here. Commented lines are replacements in case you need other comment visibility options.
	ComponentAccessor.commentManager.create(issueToComment, commentUser, commentText, null,ComponentAccessor.getComponent(ProjectRoleManager).getProjectRole("${vis}").getId(), true) //comment this line if you don't need visibility functionality.
	//ComponentAccessor.commentManager.create(issueToComment, commentUser, commentText, vis, null, true) //uncomment this line, and 'visibility' variable will set comment visibility for names of global user groups (from User Management tab)
	//ComponentAccessor.commentManager.create(issueToComment, commentUser, commentText, null, null, true) //uncomment this line if you don't need visibility functionality. All comments will have default visibility.
}


/*
Function for linked problem creation
Example: createLinkedProblem(rootIssue, listRepeatableDaily, "Problem", "Test", "Normal", "Найден ежедневно повторяющийся алерт", projectName, user, visibility)
This will create Problem linked to rootIssue with summary Test, priority Normal. Description will be like:

Найден ежедневно повторяющийся алерт.
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05
ISSUEKEY - ISSUESUMMARY Created: 1970-01-01 00:00 Resolved: 1970-01-01 00:05

Issue keys are took from incidentList (the first variable)+the issue which triggered the problem creation.
problemProjectName is for project to create issue in
user is comments' author and issue reporter.
visibility is for comments visibility

After issue creation all issues from incidentList and the issue which triggered the rule will get linked to Problem as 'is caused by' in problem.

You can set created Problem summary based on issue's description like so (just example):

String dailyProblemSummary = "[RPTISSUE] ${listRepeatableDaily.summary.get(0).replaceAll("Zabbix.* - |Prometheus |test|prod|preprod|dev", "")}"

TODO: need to rewrite it, to be able to work when incidentList is null. At this point I don't need it, since It's being triggered only for repeatable incidents of all kinds.
*/

def createLinkedProblem	 (Issue rootIssue, List<Issue> incidentList, String issueTypeName, String problemSummary, String priorityName, String shortDesc, String problemProjectName, com.atlassian.jira.user.ApplicationUser problemAuthor, String commentsVisibility) {
	def issueService = ComponentAccessor.issueService
	def constantsManager = ComponentAccessor.constantsManager
	def prioritySchemeManager = ComponentAccessor.getComponent(PrioritySchemeManager)
	def project = ComponentAccessor.projectManager.getProjectObjByKey(problemProjectName)
	assert project : "Could not find project with key $project"
	def description =shortDesc+""".
	${rootIssue.key} - ${rootIssue.summary} Created: ${rootIssue.created.toString().replaceAll(":.{2}[.].{1,3}", "")} Resolved: ${if (rootIssue.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")) == null) {return "Not resolved"} else {rootIssue.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")).toString().replaceAll(":.{2}[.].{1,3}", "")}} 
	"""
	incidentList.each {result ->; description += """ ${result.key} - ${result.summary} Created: ${result.created.toString().replaceAll(":.{2}[.].{1,3}", "")} Resolved: ${if (result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")) == null) {return "Not resolved"} else {result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")).toString().replaceAll(":.{2}[.].{1,3}", "")}} 
	"""}
	description += """ """
	
	def issueType = constantsManager.allIssueTypeObjects.findByName(issueTypeName)
	assert issueType : "Could not find issue type with name $issueTypeName"

	
	//next is some jira magic for preparing issueNewProblem to be created
	def issueContext = new IssueContextImpl(project, issueType) as IssueContext
	def priorityId = constantsManager.priorities.findByName(priorityName)?.id ?: prioritySchemeManager.getDefaultOption(issueContext)
	def component = rootIssue.getComponents()
	def issueNewProblem = ComponentAccessor.getIssueFactory().getIssue()
	issueNewProblem.setProjectId(project.id)
	issueNewProblem.setComponent(component)
	issueNewProblem.setIssueTypeId(issueType.id)
	issueNewProblem.setReporterId(problemAuthor.name)
	issueNewProblem.setFixVersions(rootIssue.fixVersions)
	issueNewProblem.setSummary(problemSummary)
	issueNewProblem.setPriorityId(priorityId)
	issueNewProblem.setDescription(description)
	def subTask = ComponentAccessor.getIssueManager().createIssueObject(problemAuthor, issueNewProblem) //this one finally creates the issueNewProblem, and after that line we can address it as regular issue
	
	def commentAboutCreatedProblem = shortDesc+". Создан ${issueTypeName} ${issueNewProblem.key}"
	makeComment(rootIssue, commentAboutCreatedProblem, problemAuthor, commentsVisibility)
	
	//linking the 'issue' which triggered the rule
	def linkType = ComponentAccessor.getComponent(IssueLinkTypeManager).issueLinkTypes.findByName("Problem/Incident")
	def destinationIssue = ComponentAccessor.issueManager.getIssueByCurrentKey(issueNewProblem.key)
	ComponentAccessor.issueLinkManager.createIssueLink(rootIssue.id, destinationIssue.id, linkType.id, 1L, problemAuthor)
	
	//linking all issues from incidentList
	incidentList.each {result ->;
	def sourceIssue = ComponentAccessor.issueManager.getIssueByCurrentKey(result.key)
	ComponentAccessor.issueLinkManager.createIssueLink(sourceIssue.id, destinationIssue.id, linkType.id, 1L, problemAuthor)
	}
}

/*
findRepeatableIssues - simple function for finding patterns in incoming incidents. Finds repeatable issues with same component and description based on settings. E.g.:
findRepeatableIssues (7, 1, 15, currentIssue) will subtract 1 day from currentIssue's created day and will check +-15minutes period of time. If it finds 1 issue with same component and summary in that period - it will write it to the list.
It will complete the same 7 times.
The result of the function is a list of issues which were found.
*/


public List<Issue>  findRepeatableIssues (Integer numberOfSteps, Integer stepDurationDays, Integer minutesGap, Issue issueAnalyzed) {
	List<Issue> listRepeatable = new ArrayList<Issue>()
	for (Integer i = 1; i < numberOfSteps+1; i++) {
	/*
	Next two lines are ugly as hell, but it works. Probably did a bad job trying to match all those timestamps/dates and JQL requirements. Will mark it as TODO, this definetely can and must be simplified.
	replaceAll at the end is required since JQL don't want to work when Timestamp has seconds and milliseconds, so this regexp just removes them.
	also defining variables in each iteration feels wrong to me, TODO rewriting that part.
	*/
		def checkFrom = "${(new Date(issueAnalyzed.created.getTime() - i*(stepDurationDays*86400000) - minutesGap*60*1000)).toTimestamp()}".replaceAll(":.{2}[.].{1,3}", "") //this sets starting time to find repeatable issues. 86400000 is 24hours in milliseconds.
		def checkTill = "${(new Date(issueAnalyzed.created.getTime()- i*(stepDurationDays*86400000) + minutesGap*60*1000)).toTimestamp()}".replaceAll(":.{2}[.].{1,3}", "") //this sets ending time to find repeatable issues. 86400000 is 24hours in milliseconds
		def queryRepeatable = "type = Incident AND project = ${"${issueAnalyzed.key}".replaceAll(/-.*/, "")} AND component = ${issueAnalyzed.getComponents().head().name} AND summary ~ \"${issueAnalyzed.summary}\" AND createdDate > \"${checkFrom}\" and createdDate < \"${checkTill}\" ORDER BY created DESC"
		def resultsRepeatable = ComponentAccessor.getComponent(SearchService.class).search(ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser(), ComponentAccessor.getComponent(JqlQueryParser).parseQuery(queryRepeatable), PagerFilter.getUnlimitedFilter())
		if (resultsRepeatable.total == 1) { //maybe should use > 1 instead. In such case, need to change listRepeatableDaily defining to work with all results (not only the first as now). But in current design and workload I need == 1. Can hardly imagine more than 1 incident for Component + Summary created in 30 minutes gap per Project.
		listRepeatable.add(resultsRepeatable.results.get(0))
		}
	}
	return listRepeatable
}


//Next part is for simple jql based analyze of issue. Lots of comments and all is based on 'if' conditions. TODO: rewrite it to be a bit more human-friendly

def queryWithoutLinkLast2Days = "type = Incident AND project = ${projectName} AND component = ${issueComponent} AND issueLinkType != causes AND createdDate > -48h AND issuekey != ${currentIssue.key} ORDER BY created DESC"
def queryOpenedProblems = "type != Incident AND project = ${projectName} AND issueFunction in linkedIssuesOf(\"type=Incident AND component = ${issueComponent}\", causes) AND status != Closed ORDER BY created DESC"
def queryRepeatable12h = "type = Incident AND project = ${projectName} AND component = ${issueComponent} AND summary ~ \"${currentIssueSummary}\" AND createdDate > -12h ORDER BY created DESC"
def queryOpenedLinkedHit = "type != Incident AND project = ${projectName} AND issueFunction in linkedIssuesOf(\"type=Incident AND component = ${issueComponent} and summary ~\'${currentIssueSummary}\'\", causes) AND status != Closed ORDER BY created DESC"
def queryOpenIncidents = "type = Incident AND project = ${projectName} AND component = ${issueComponent} AND status != Closed ORDER BY created DESC"
def queryRepeatable48h = "type = Incident AND project = ${projectName} AND component = ${issueComponent} AND summary ~ \"${currentIssueSummary}\" AND createdDate > -48h AND issuekey != ${currentIssue.key} AND issueLinkType != causes ORDER BY created DESC"
def queryRepeatable7d = "type = Incident AND project = ${projectName} AND component = ${issueComponent} AND summary ~ \"${currentIssueSummary}\" AND createdDate > -7d AND issuekey != ${currentIssue.key} AND issueLinkType != causes ORDER BY created DESC"

def resultsWithoutLinkLast2Days = searchService.search(user, jqlQueryParser.parseQuery(queryWithoutLinkLast2Days), pager)
def resultsOpenedProblems = searchService.search(user, jqlQueryParser.parseQuery(queryOpenedProblems), pager)
def resultsRepeatable12h = searchService.search(user, jqlQueryParser.parseQuery(queryRepeatable12h), pager)
def resultsOpenedLinkedHit = searchService.search(user, jqlQueryParser.parseQuery(queryOpenedLinkedHit), pager)
def resultsOpenIncidents = searchService.search(user, jqlQueryParser.parseQuery(queryOpenIncidents), pager)
def resultsRepeatable48h = searchService.search(user, jqlQueryParser.parseQuery(queryRepeatable48h), pager)
def resultsRepeatable7d = searchService.search(user, jqlQueryParser.parseQuery(queryRepeatable7d), pager)

//Next part is related to repeatable issues analyze. 
List<Issue> listRepeatableDaily = new ArrayList<Issue>()
listRepeatableDaily = findRepeatableIssues (7, 1, repeatableCheckGap, currentIssue)
List<Issue> listRepeatableWeekly = new ArrayList<Issue>()
listRepeatableWeekly = findRepeatableIssues (5, 7, repeatableCheckGap, currentIssue)

/* 
The part with comments.
Unfortunately at this point all those if's seem to be required, since I'm not only giving raw information, but also making comment decisions based on results combinations. 
And AFAIK there are no conditional Switch/Case in Java
*/

def String commentBody= ""

if (resultsOpenedProblems.total > 0 && resultsWithoutLinkLast2Days.total == 0 && resultsOpenedLinkedHit.total == 0) {
	commentBody += """По компоненту существуют открытые связанные заявки, в которых может производиться исследование проблем: [${resultsOpenedProblems.total}|${jqlToUrl(queryOpenedProblems)}]
	Точного наличия открытого исследования по этому алерту установить не удалось."""+emptyLine
}

if (resultsOpenedProblems.total > 0 && resultsWithoutLinkLast2Days.total == 0 && resultsOpenedLinkedHit.total > 0) {
	commentBody += """Найдена открытая заявка(и), в которой производится исследование этого алерта: [${resultsOpenedLinkedHit.total}|${jqlToUrl(queryOpenedLinkedHit)}]
	Также, по компоненту существуют открытые связанные заявки (могут совпадать с вышенайденными): [${resultsOpenedProblems.total}|${jqlToUrl(queryOpenedProblems)}]"""+emptyLine
}

if (resultsOpenedProblems.total > 0 && resultsWithoutLinkLast2Days.total > 0 && resultsOpenedLinkedHit.total == 0) {
	commentBody += """По компоненту существуют открытые связанные заявки: [${resultsOpenedProblems.total}|${jqlToUrl(queryOpenedProblems)}]
	За последние двое суток инцидентов по этому компоненту, не связанных с другими заявками: [${resultsWithoutLinkLast2Days.total}|${jqlToUrl(queryWithoutLinkLast2Days)}]
	Точного наличия открытого исследования по этому алерту установить не удалось. Создайте его, если это необходимо"""+emptyLine
}

if (resultsOpenedProblems.total > 0 && resultsWithoutLinkLast2Days.total > 0 && resultsOpenedLinkedHit.total > 0) {
	commentBody += """Найдена открытая заявка(и), в которой производится исследование этого алерта: [${resultsOpenedLinkedHit.total}|${jqlToUrl(queryOpenedLinkedHit)}]
	Также, по компоненту существуют открытые связанные заявки (могут совпадать с вышенайденными): [${resultsOpenedProblems.total}|${jqlToUrl(queryOpenedProblems)}]
	За последние двое суток инцидентов по этому компоненту, не связанных с другими заявками: [${resultsWithoutLinkLast2Days.total}|${jqlToUrl(queryWithoutLinkLast2Days)}]"""+emptyLine
}

if (resultsOpenedProblems.total == 0 && resultsWithoutLinkLast2Days.total > 5) {
	commentBody += """Открытых связанных заявок, в которых может производиться исследование, по этому компоненту не найдено, но число инцидентов с этого сервера за последние 48 часов - [${resultsWithoutLinkLast2Days.total}|${jqlToUrl(queryWithoutLinkLast2Days)}] 
 Рассмотрите найденные заявки, возможно необходимо создать Problem."""+emptyLine
}

if (resultsRepeatable12h.total > 3 && resultsOpenedProblems.total == 0) {
	commentBody += """Открытых связанных заявок по этому компоненту не найдено, но число подобных алертов за последние 12 часов превысило 3. Текущее число: [${resultsRepeatable12h.total}|${jqlToUrl(queryRepeatable12h)}] 
 Возможно необходимо создать Problem и исследовать причину повторения алертов."""+emptyLine
}

/*
The next thing 'result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time"))' is to get a date from custom field used in our company. It can be safely replaced with 'result.resolutiondate' or any other.
The whole 'if' construction afterwards is just to avoid 'null' in comment output.
replaceAll(":.{2}[.].{1,3}", "") is for beautify only, I don't need seconds and milliseconds. 
*/
if (listRepeatableDaily.size() >= 2) {
	def commentRepeatableDaily = """Этот алерт возможно повторяется каждый день. За последнюю неделю найдены следующие заявки, приходящие примерно в одно время:"""+newLine
	listRepeatableDaily.each {result ->; commentRepeatableDaily += """ ${result.key} - ${result.summary} Created: ${result.created.toString().replaceAll(":.{2}[.].{1,3}", "")} Resolved: ${if (result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")) == null) {return "Not resolved"} else {result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")).toString().replaceAll(":.{2}[.].{1}", "")}}"""+newLine}
	commentRepeatableDaily += """Проверьте вышенайденные заявки, и если необходимо создайте Problem для исследования."""+emptyLine
	commentBody += commentRepeatableDaily
}

if (listRepeatableWeekly.size() >= 2) {
	def commentRepeatableWeekly = """Этот алерт возможно повторяется каждую неделю. За последние 4 недели найдены следующие заявки, приходящие примерно в одно время:"""+newLine
	listRepeatableWeekly.each {result ->; commentRepeatableWeekly += """ ${result.key} - ${result.summary} Created: ${result.created.toString().replaceAll(":.{2}[.].{1,3}", "")} Resolved: ${if (result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")) == null) {return "Not resolved"} else {result.getCustomFieldValue(ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Inc Good Time")).toString().replaceAll(":.{2}[.].{1}", "")}}"""+newLine}
	commentRepeatableWeekly += """Проверьте вышенайденные заявки, и если необходимо создайте Problem для исследования."""+emptyLine
	commentBody += commentRepeatableWeekly
}

if (resultsOpenIncidents.total > 2) {
	commentBody += """*Обнаружены открытые алерты по этому компоненту. Текущее число:* [${resultsOpenIncidents.total}|${jqlToUrl(queryOpenIncidents)}] 
 *Возможно проблемы не ограничиваются этим алертом и необходимо исследовать ситуацию в комплексе*"""+emptyLine
}

if (commentBody !=  "") {
makeComment(currentIssue, commentBody, user, visibility)
}

/*
Part with actions, like creating tickets or linking
*/

if (resultsOpenedLinkedHit.total == 1) {
	def linkType = ComponentAccessor.getComponent(IssueLinkTypeManager).issueLinkTypes.findByName("Problem/Incident")
	def destinationIssue = ComponentAccessor.issueManager.getIssueByCurrentKey(resultsOpenedLinkedHit.results.key.get(0))

	ComponentAccessor.issueLinkManager.createIssueLink(currentIssue.id, destinationIssue.id, linkType.id, 1L, user)
	String commentIssueLinked = """Заявка прикреплена к ${destinationIssue}.
	Проверьте ${destinationIssue} и убедитесь что этот алерт с ним связан. Удалите связь, если прикрепление ошибочно."""
	makeComment(currentIssue, commentIssueLinked, user, visibility)
}

def problemSummary = "[RPTISSUE] ${currentIssue.summary.replaceAll(summaryBeautificationRegexp, "")}" //this variable can be used to choose summary based on some condition, e.g based on projectName. Currently is just removes some words from issue summary based on my needs
def descriptionHeader = "" //default
def List<Issue> listIssues 

if (resultsRepeatable48h.total > thresholdRepeatable48h && resultsOpenedLinkedHit.total == 0 && !createIssueFlag) {
	listIssues = resultsRepeatable48h.getResults()
	descriptionHeader = "Алерт приходил слишком часто за последние 2 суток"
	linkedIssuePriority = "High" //setting non-default priority for linked issue creation
	createIssueFlag = true 
}

if (resultsRepeatable7d.total > thresholdRepeatable7d && resultsOpenedLinkedHit.total == 0 && !createIssueFlag) {
	listIssues = resultsRepeatable7d.getResults()
	descriptionHeader = "Алерт приходил слишком часто за последнюю неделю"
	linkedIssuePriority = "High"
	createIssueFlag = true 
}

if (listRepeatableDaily.size() >= thresholdRepeatableDaily && resultsOpenedLinkedHit.total == 0 && !createIssueFlag) {
	descriptionHeader = "Найден ежедневно повторяющийся алерт"
	listIssues = listRepeatableDaily
	createIssueFlag = true 
}

if (listRepeatableWeekly.size() >= thresholdRepeatableWeekly && resultsOpenedLinkedHit.total == 0 && !createIssueFlag) {
	listIssues = listRepeatableWeekly
	descriptionHeader = "Найден еженедельно повторяющийся алерт"
	createIssueFlag = true
}


//Linked issue creation part. All variables should be defined earlier in the code

if (createIssueFlag) {
	createLinkedProblem(currentIssue, listIssues, "Problem", problemSummary, linkedIssuePriority, descriptionHeader, projectName, user, visibility)
}
