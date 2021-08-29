import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.security.roles.ProjectRoleManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.customfields.option.Option
import com.atlassian.jira.event.type.EventDispatchOption
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.context.IssueContext
import com.atlassian.jira.issue.context.IssueContextImpl

def currentIssue = ComponentAccessor.getIssueManager().getIssueObject(issue.key)
def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()



def setProductName (String parentValue, String childValue, String issueToUpdateKey, com.atlassian.jira.user.ApplicationUser updateUser) {
    def issueToUpdate = ComponentAccessor.getIssueManager().getIssueObject(issueToUpdateKey)
	def issueManager = ComponentAccessor.getIssueManager()
	def customFieldManager = ComponentAccessor.getCustomFieldManager()
	def optionsManager = ComponentAccessor.optionsManager
	def customField = ComponentAccessor.customFieldManager.getCustomFieldObjects(issueToUpdate.projectId, issueToUpdate.issueTypeId).findByName("Product Name")
	def productName = customFieldManager.getCustomFieldObjectsByName("Product Name")
	def fieldConfig = customField.getRelevantConfig(issueToUpdate)
	def options = optionsManager.getOptions(fieldConfig)
	def parentOption = options.find { it.value == parentValue }
	def childOption = parentOption?.childOptions?.find { it.value == childValue }
	def finalProductName = new HashMap<>()
	finalProductName.put(null, parentOption)
	finalProductName.put("1", childOption)
	issueToUpdate.setCustomFieldValue(productName.iterator().next(), finalProductName)
	issueManager.updateIssue(updateUser, issueToUpdate, EventDispatchOption.DO_NOT_DISPATCH, false)
}

def description = currentIssue.description
def pName
def technology = (description =~ /(?<=technology: ).*/).findAll()
if (technology.size() < 1) { technology = ""} else {technology = technology.first()}

switch (technology) {
    case "postgres": pName = "postgres"
    break
    case "mongo": pName = "mongo"
    break
    case "kafka": pName = "kafka"
    break
    case "elastic": pName = "elastic"
    break
    case "victoriametrics": pName = "monitoring"
    break
	case "rabbit": pName = "rabbit"
    break
	case "mysql": pName = "mysql"
    break
    default:
        if ((currentIssue.description =~ /Postgres|postgres|postgre|PostgreSQL|POSTGRES/).findAll().size() > 0 || (currentIssue.summary =~ /Postgres|postgres|postgre|PostgreSQL/).findAll().size() > 0  ) {
			pName = "postgres"
		}
		else if ((currentIssue.description =~ /Mongo|mongo|MONGO|MongoDB|MONGODB/).findAll().size() > 0 || (currentIssue.summary =~ /Mongo|mongo|MONGO|MongoDB|MONGODB/).findAll().size() > 0  ) {
			pName = "mongo"
		}
		else if ((currentIssue.description =~ /kafka|Kafka|KAFKA/).findAll().size() > 0 || (currentIssue.summary =~ /kafka|Kafka|KAFKA/).findAll().size() > 0  ) {
			pName = "kafka"
		}
		else if ((currentIssue.description =~ /ELK|ELASTIC|elastic|Elastic|Elasticsearch/).findAll().size() > 0 || (currentIssue.summary =~ /ELK|ELASTIC|elastic|Elastic|Elasticsearch/).findAll().size() > 0  ) {
			pName = "elastic"
		}
		else if ((currentIssue.description =~ /RABBITMQ|RABBIT|rabbit|rabbitmq/).findAll().size() > 0 || (currentIssue.summary =~ /RABBITMQ|RABBIT|rabbit|rabbitmq/).findAll().size() > 0  ) {
			pName = "rabbit"
		}
		else if ((currentIssue.description =~ /MYSQL|mysql|mySQL|MySQL|Mysql|mySQL/).findAll().size() > 0 || (currentIssue.summary =~ /MYSQL|mysql|mySQL|MySQL|Mysql|mySQL/).findAll().size() > 0  ) {
			pName = "mysql"
		}
		else {
			pName = "unix"
		}
    break
}

switch (pName) {
	case "postgres": 
		setProductName("DB", "POSTGRES", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "PostgreSQL_24/7")])
    break
    case "mongo": 
		setProductName("DB", "MONGO", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "MongoDB_24/7")])
    break
    case "kafka": 
		setProductName("OTHER", "Kafka", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "Kafka_24/7")])
    break
    case "elastic": 
		setProductName("DB", "ELASTIC", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "Elasticsearch_24/7")])
    break
    case "monitoring": 
		setProductName("OTHER", "Monitoring", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "Support_24/7")])
    break
	case "rabbit": 
		setProductName("DB", "RABBITMQ", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "RabbitMQ_24/7")])
    break
	case "mysql": 
		setProductName("DB", "MYSQL", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "MySQL/MariaDB_24/7")])
    break
	case "unix": 
		setProductName("OS", "UNIX", currentIssue.key, user)
		currentIssue.setFixVersions([ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, "Support_24/7")])
    break
    default:
	break
}


def project = (description =~ /(?<=project: ).*/).findAll()
if (project.size() < 1) { project = ""} else {project = project.first()}
def fixVersion=ComponentAccessor.versionManager.getVersion(currentIssue.getProject().id, project)

if (fixVersion) {
	currentIssue.setFixVersions([fixVersion])
}


ComponentAccessor.getIssueManager().updateIssue(user, currentIssue, EventDispatchOption.DO_NOT_DISPATCH, false)
