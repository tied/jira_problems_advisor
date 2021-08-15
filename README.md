<h1 align="center">jira_problems_advisor</h1>

<div align="center">Scriptrunner's script for Technical Support Incident automation</div>

**WARNING:** Script is under Development and wasn't tested in any other JIRA environments except the one used in my company
**USE IT ON YOUR OWN RISK**

## What is this and what is it for?️

This is a script which is supposed to be used as an action in Jira's Project Automation.
It performs some basic analyze on incoming Incident, trying to find if there are any related Incidents, or if this incident is already being investigated in another issue.
Also, based on it's findings, it can automatically create linked Problems for further deeper investigation of the Incidents.
All these is required to improve Problem Management process of first and seconds lines of Tech Support.

## What is required for it to work?

- Scriptrunner (obviously). I've tested it on 6.31.0
- Jira (obviously). I've tested it on 8.8.0 only, but versions below also should be good.
- Issues must have at least one Component (in our company we set Component to server's hostname, so script's JQL use components to identify the server)
- Component must be unique enough to group all your incoming alerts in rather small group. E.g. Component = Server's Hostname.
- Issues summary must be pretty unique, and should not contain any variables that change for one Incident from time to time, since both Component and Summary are used to group issues. E.g:

```
//Good for script
Diskspace / on hostname1.domain
Diskspace /var on hostname2.domain
Load Average is high on hostname2.domain
CPU Usage is high on hostname3.domain
```

```
//Bad for script
Diskspace / is 68% used on hostname1.domain
Diskspace /var is 11% used on hostname2.domain
Diskspace / is 91% used on hostname2.domain
Load Average is 8.5 on hostname2.domain
Load Average is 9.3 on hostname2.domain
```
- Jira must have 'Issue linking' enabled. By default script is using link name 'Problem/Incident' with Outward and Inward Description causes/is caused by respectively

## What it can do?

- Based on Component and Issue links: find and report to comment in ticket Incidents and Problems that are already investigate some issues related to the component current Issue got created with.
- Reports any strange behavior (e.g. there are too many opened Incidents with this component, or there were too many such Incidents in last 12h, etc)
- Automatically link current issue to the problem, if it finds open Problem which already has Incident with same Summary and Component linked to it
- Find Repeatable Incidents (which get created on Daily or Weekly basis at approximately same time)
- Automatically creates a Problem and links all related Incidents to it if it finds repeatable Incident of any kind which has no Problem where it is being investigated
- [Almost] Automatically creates a Problem if it finds any strange behavior

## Installation
Since this is not a plugin, but just a couple of automation scripts, some manual steps are required to make it work. Most likely you must have Admin access to Jira, since 'Scriptrunner script' action for automation available only to Admins.
If you worked with Jira Automation before - you probably know what you're doing. If you haven't - please be extra careful. You'd better try some of it's functionallity first to not only follow the instructions but understand what you are doing.
0) Make sure that you fit the requirements in [What is required for it to work?](#what-is-required-for-it-to-work) section
1) Go to Project Settings -> Project Automation and click 'Create Rule' button
2) In 'New Trigger' section choose 'Issue Created' and Save.
3) In 'Add component' section choose 'New Condition' -> 'If / else block'. Click 'Add conditions...', and select 'Issue fields condition'. Select 'Creator' 'is one of' and type the user or users which are doing automated Incident creation in you Jira. Click Save.
4) Click your created condition on the left, and click on 'Add conditions...' again. select 'Issue fields condition'. Select 'Issue Type' 'equals' and select Incident from dropdown menu (it can be any type, but at the moment script has Incident type hardcoded, though it can be easily be changed). Click Save.
5) On the left, click on (+) sign relative to newly created "If: all match" block. Choose New action. Select 'Execute a ScriptRunner script' action.
6) On the 'Note' enter any desciption you need. Then copy the content of 'possible_problem_advisor' and paste it to Script field. Wait for some time till script got analyzed. May get 4 lines with exclamation marks, it's ok. Be sure there are no other Errors. Click Save.
7) Name your automation rule and click Turn it on.

If you also need problem analyzer, then create another rule
1) Choose Manual trigger and select a group(s) of Users who will see a button in tickets. Click Save.
2) Choose New Action and select 'Execute a ScriptRunner script' action.
3) On the 'Note' enter any desciption you need. Then copy the content of 'problem_analyzer' and paste it to Script field. Wait for some time till script got analyzed. You might get one Warning and one error in timeInRed definition. Ignore it and click Save.
4) Name your automation rule (note that it's name will appear in Issue menu) and click Turn it on.
5) Now all selected group of users must see additional option in issues' More dropdown list.
