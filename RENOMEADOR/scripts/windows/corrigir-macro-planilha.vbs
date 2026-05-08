Option Explicit

Const vbext_pk_Proc = 0

Dim args
Set args = WScript.Arguments
If args.Count <> 1 Then
  WScript.Echo "Uso: corrigir-macro-planilha.vbs C:\caminho\PLANILHA_FISCAL.xlsm"
  WScript.Quit 1
End If

Dim workbookPath
workbookPath = args.Item(0)

Dim fso
Set fso = CreateObject("Scripting.FileSystemObject")
If Not fso.FileExists(workbookPath) Then
  WScript.Echo "ERRO: planilha nao encontrada: " & workbookPath
  WScript.Quit 2
End If
workbookPath = fso.GetAbsolutePathName(workbookPath)

Dim excel, workbook
On Error Resume Next
Set excel = CreateObject("Excel.Application")
If Err.Number <> 0 Then
  WScript.Echo "ERRO: Excel nao disponivel: " & Err.Description
  WScript.Quit 3
End If
On Error GoTo 0

excel.Visible = False
excel.DisplayAlerts = False
excel.EnableEvents = False

On Error Resume Next
Set workbook = excel.Workbooks.Open(workbookPath)
If Err.Number <> 0 Then
  WScript.Echo "ERRO: nao foi possivel abrir a planilha: " & Err.Description
  excel.Quit
  WScript.Quit 4
End If
On Error GoTo 0

Dim vbProject
On Error Resume Next
Set vbProject = workbook.VBProject
If Err.Number <> 0 Then
  WScript.Echo "ERRO: Excel bloqueou acesso ao projeto VBA."
  WScript.Echo "Ative: Arquivo > Opcoes > Central de Confiabilidade > Configuracoes de Macro > Confiar no acesso ao modelo de objeto do projeto VBA."
  workbook.Close False
  excel.Quit
  WScript.Quit 5
End If
On Error GoTo 0

RemoveSheetFolderPickerHandlers vbProject, workbook.CodeName
InstallWorkbookFolderPickerHandler vbProject, workbook.CodeName

workbook.Save
workbook.Close True
excel.Quit

WScript.Echo "Macro de duplo clique corrigida para todas as abas CADASTRO."
WScript.Quit 0

Sub InstallWorkbookFolderPickerHandler(project, workbookCodeName)
  Dim component, codeModule
  Set component = project.VBComponents(workbookCodeName)
  Set codeModule = component.CodeModule
  DeleteProcedureIfPresent codeModule, "Workbook_SheetBeforeDoubleClick"
  codeModule.AddFromString WorkbookDoubleClickCode()
End Sub

Sub RemoveSheetFolderPickerHandlers(project, workbookCodeName)
  Dim component, codeModule, startLine, lineCount, procedureText
  For Each component In project.VBComponents
    If component.Name <> workbookCodeName Then
      Set codeModule = component.CodeModule
      On Error Resume Next
      startLine = codeModule.ProcStartLine("Worksheet_BeforeDoubleClick", vbext_pk_Proc)
      If Err.Number = 0 Then
        lineCount = codeModule.ProcCountLines("Worksheet_BeforeDoubleClick", vbext_pk_Proc)
        procedureText = codeModule.Lines(startLine, lineCount)
        If InStr(1, procedureText, "msoFileDialogFolderPicker", vbTextCompare) > 0 Then
          codeModule.DeleteLines startLine, lineCount
        End If
      End If
      Err.Clear
      On Error GoTo 0
    End If
  Next
End Sub

Sub DeleteProcedureIfPresent(codeModule, procedureName)
  Dim startLine, lineCount
  On Error Resume Next
  startLine = codeModule.ProcStartLine(procedureName, vbext_pk_Proc)
  If Err.Number = 0 Then
    lineCount = codeModule.ProcCountLines(procedureName, vbext_pk_Proc)
    codeModule.DeleteLines startLine, lineCount
  End If
  Err.Clear
  On Error GoTo 0
End Sub

Function WorkbookDoubleClickCode()
  WorkbookDoubleClickCode = _
    "Private Sub Workbook_SheetBeforeDoubleClick(ByVal Sh As Object, ByVal Target As Range, Cancel As Boolean)" & vbCrLf & _
    "    If Left(UCase(Sh.Name), 9) <> ""CADASTRO "" Then Exit Sub" & vbCrLf & _
    "    If Target.CountLarge <> 1 Then Exit Sub" & vbCrLf & _
    "    If Target.Row < 3 Then Exit Sub" & vbCrLf & _
    "    If Target.Column < 17 Or Target.Column > 20 Then Exit Sub" & vbCrLf & _
    "    Cancel = True" & vbCrLf & _
    "    With Application.FileDialog(msoFileDialogFolderPicker)" & vbCrLf & _
    "        .Title = ""Selecionar Pasta""" & vbCrLf & _
    "        If Target.Value <> """" Then .InitialFileName = Target.Value & ""\""" & vbCrLf & _
    "        .AllowMultiSelect = False" & vbCrLf & _
    "        If .Show = -1 Then" & vbCrLf & _
    "            Target.Value = .SelectedItems(1)" & vbCrLf & _
    "            Target.Interior.Color = RGB(198, 239, 206)" & vbCrLf & _
    "            Target.Font.Italic = False" & vbCrLf & _
    "            Target.Font.Bold = False" & vbCrLf & _
    "            Target.Font.Size = 10" & vbCrLf & _
    "        End If" & vbCrLf & _
    "    End With" & vbCrLf & _
    "End Sub" & vbCrLf
End Function
