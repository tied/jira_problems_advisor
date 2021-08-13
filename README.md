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
- Automatically creates a Problem and links all related Incidents to it if it finds repeatable Incident which has no Problem where it is being investigated
- [Not yet] Automatically creates a Problem if it finds any strange behavior

