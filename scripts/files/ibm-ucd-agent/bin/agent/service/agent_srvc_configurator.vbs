' Licensed Materials - Property of IBM Corp.
' IBM UrbanCode Build
' IBM UrbanCode Deploy
' IBM UrbanCode Release
' IBM AnthillPro
' (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
'
' U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
' GSA ADP Schedule Contract with IBM Corp.
dim login, passwd, startmode, aloop, StdIn, fso, file, agenthome
agenthome = "@AGENT_HOME@"
Set StdIn = WScript.StdIn
serviceName = WScript.Arguments.Item(0)
aloop = true
strComputer = "."

'--- account warning

WScript.Echo "Service must be started manually after installation or if you set autostart option it will start automatically after system reboot." & vbCrLf
WScript.Echo "Note!" & vbCrLf & "You can only run the service as account with 'Log On as Service' rights." & vbCrLf & "To add the rights to an account you can do following:" & vbCrLf & "1. Click Start > Settings > Control Panel." & vbCrLf & "2. Double-click Administrative Tools." & vbCrLf & "3. Double-click Local Security Policy." & vbCrLf & "4. Open Local Policies." & vbCrLf & "5. Open User Rights Assignment." & vbCrLf & "6. Open Log On as a Service." & vbCrLf & "7. Click Add." & vbCrLf & "8. Select the user you want to grant logon service access to and click OK." & vbCrLf & "9. Click OK to save the updated policy." & vbCrLf

'--- gathering service parameters


Do While aloop = True

    WScript.Echo "Enter the user account name including domain path to run the service as (for local use '.\' before login ), by default will be used local system account. [Default: '.\localsystem']"
    login = StdIn.ReadLine
    
    If login <> "" And login <> ".\localsystem" Then
        WScript.Echo "Please enter your password for desired account."
        passwd = StdIn.ReadLine
    End If

    WScript.Echo "Do you want to start the '" & serviceName & "' service automatically? y,N [Default: N]"
    startmode = StdIn.ReadLine

    Set objWMIService = GetObject("winmgmts:" _
        & "{impersonationLevel=impersonate}!\\" & strComputer & "\root\cimv2")
    Set colRunningServices = objWMIService.ExecQuery _
        ("Select * from Win32_Service where Name = '" & serviceName & "'")

    For Each objService in colRunningServices
        If login >= "" Then
            If login = "" Or login = ".\localsystem" then
                errServiceChange = objService.Change( , , , , , , ".\localsystem" , "")
            Else
                errServiceChange = objService.Change( , , , , , , login , passwd)
            End If
        End If
    
        If (startmode = "y" Or startmode = "Y" Or startmode = "yes" Or startmode = "YES" Or startmode = "Yes") Then
            objService.ChangeStartMode("Automatic")
        Else 
            objService.ChangeStartMode("Manual")
        End If
    Next

    If login >= "" Then
        If errServiceChange = 22 Then
            WScript.Echo "Wrong domain path (for local account add '.\' before your login name) or a given account doesn't exist. Try again." & vbCrLf
            aloop = True
        Elseif errServiceChange > 0 Then
            WScript.Echo "ERROR:" & errServiceChange & " during service configuration." & vbCrLf
            aloop = False
        Elseif errServiceChange = 0 Then
            aloop = False
            WScript.Echo "Service '" & serviceName & "' has been successfully installed and configured." & vbCrLf
            WScript.Echo "Start it manually or reboot system if your service has set an autostart option." & vbCrLf            
        End If
    End If
Loop
