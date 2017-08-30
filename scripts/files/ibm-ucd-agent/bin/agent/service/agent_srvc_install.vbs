' Licensed Materials - Property of IBM Corp.
' IBM UrbanCode Build
' IBM UrbanCode Deploy
' IBM UrbanCode Release
' IBM AnthillPro
' (c) Copyright IBM Corporation 2002, 2014. All Rights Reserved.
'
' U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
' GSA ADP Schedule Contract with IBM Corp.
dim srvc, installOK, login, passwd, startmode, fso, file, StdIn
Set StdIn = WScript.StdIn
srvc = False
installOK = False
strServiceName = WScript.Arguments.Item(0)
login = WScript.Arguments.Item(1)
passwd = WScript.Arguments.Item(2)
startmode = WScript.Arguments.Item(3)
strComputer = "."

'--- checking if given service already exists

    WScript.Echo "Checking if service with given name is already installed..."


    Set objWMIService = GetObject("winmgmts:" _
        & "{impersonationLevel=impersonate}!\\" & strComputer & "\root\cimv2")
    Set colRunningServices = objWMIService.ExecQuery _
        ("Select * from Win32_Service where Name = '" & strServiceName & "'")
    
    If colRunningServices.Count > 0 Then
        For Each objCurrService in colRunningServices
            If objCurrService.Name = strServiceName Then
                WScript.Echo "You have already installed service named '" & objCurrService.Name & "'."
                WScript.Echo "Aborting sevice installation, you can install it later manually (see documentation)." & vbCrLf
                Set fso = CreateObject("Scripting.FileSystemObject")
                Set file = fso.CreateTextFile("srvc.properties", True)
                file.WriteLine("install.service.status=failed")
                file.Close                
                srvc = True
            end if
        Next
            
'--- calling jvm service installation script
    Else
        WScript.Echo "Service '" & strServiceName & "' is not installed. Installing..."
        Dim oWSH: Set oWSH = CreateObject( "WScript.Shell" )
        Dim nRet: nRet = oWSH.Run( "_agent.cmd install " & strServiceName, 0, True  )  
		If nRet <> 0 Then
			WScript.Echo "Error found. Exiting with error code '" & nRet &"'"
			Wscript.Quit(nRet)
		end if 
        WScript.Echo "Service '" & strServiceName & "' has been successfully installed."
        Set fso = CreateObject("Scripting.FileSystemObject")
        Set file = fso.CreateTextFile("srvc.properties", True)
        file.WriteLine("install.service.status=OK")
        file.Close                
        installOK = True
    End If
    

'--- setting the service parameters

    If (srvc = False And installOK = True) Then

        Set objCurrService = objWMIService.Get("Win32_Service.Name='" & strServiceName & "'")

        If (objCurrService.Name = strServiceName) Then
    
            If login >= "" Then
                If (login = "" Or login = ".\localsystem") Then
                    errServiceChange = objCurrService.Change( , , , , , , ".\localsystem" , "")
                Else
                    errServiceChange = objCurrService.Change( , , , , , , login , passwd)
                End If
            End If
    
            If login >= "" Then
                If errServiceChange = 22 Then
                    WScript.Echo "Wrong domain path (for local account add '.\' before your login name) or a given account doesn't exist. You must set these values manually later." & vbCrLf
                ElseIf errServiceChange > 0 Then
                    WScript.Echo "ERROR:" & errServiceChange & " during service configuration. Reconfigure parameters manually." & vbCrLf                    
                End If
            End If
            
            If (startmode = "y" Or startmode = "Y" Or startmode = "yes" Or startmode = "YES" Or startmode = "Yes" Or startmode = "true" Or startmode = "TRUE" Or startmode = "True") Then
                objCurrService.ChangeStartMode("Automatic")
                startStatus = objCurrService.StartService()
                
                If startStatus = 0 Then
                    WScript.Echo "Agent service " & objCurrService.Name & " started successfully"
                Else
                    WScript.Echo "Agent service " & objCurrService.Name & " failed to start. Return code: " & startStatus
                End If
            Else 
                objCurrService.ChangeStartMode("Manual")
            End If
        End If
    End If
