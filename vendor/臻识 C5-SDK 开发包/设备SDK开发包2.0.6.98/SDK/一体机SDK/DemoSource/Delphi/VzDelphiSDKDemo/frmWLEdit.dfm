object FormWLEdit: TFormWLEdit
  Left = 192
  Top = 130
  Width = 348
  Height = 299
  Caption = #20462#25913#30333#21517#21333
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  Position = poScreenCenter
  PixelsPerInch = 96
  TextHeight = 13
  object lbl1: TLabel
    Left = 48
    Top = 56
    Width = 57
    Height = 13
    AutoSize = False
    Caption = #36710#29260#21495':'
    WordWrap = True
  end
  object lbl2: TLabel
    Left = 48
    Top = 96
    Width = 73
    Height = 13
    AutoSize = False
    Caption = #26159#21542#21551#29992':'
    WordWrap = True
  end
  object lbl3: TLabel
    Left = 48
    Top = 136
    Width = 73
    Height = 13
    AutoSize = False
    Caption = #36807#26399#26102#38388':'
    WordWrap = True
  end
  object lbl4: TLabel
    Left = 48
    Top = 176
    Width = 73
    Height = 13
    AutoSize = False
    Caption = #26159#21542#25253#35686':'
    WordWrap = True
  end
  object btnWLOK: TButton
    Left = 96
    Top = 216
    Width = 65
    Height = 25
    Caption = #30830#23450
    TabOrder = 0
    OnClick = btnWLOKClick
  end
  object btnWLCancel: TButton
    Left = 208
    Top = 216
    Width = 57
    Height = 25
    Caption = #21462#28040
    TabOrder = 1
    OnClick = btnWLCancelClick
  end
  object edtWLPlate: TEdit
    Left = 128
    Top = 48
    Width = 113
    Height = 21
    TabOrder = 2
  end
  object chkIsEnable: TCheckBox
    Left = 128
    Top = 96
    Width = 49
    Height = 17
    Caption = #26159
    TabOrder = 3
  end
  object chkIsAlarm: TCheckBox
    Left = 128
    Top = 176
    Width = 49
    Height = 17
    Caption = #26159
    TabOrder = 4
  end
  object dtpWLDate: TDateTimePicker
    Left = 128
    Top = 136
    Width = 97
    Height = 21
    Date = 42651.431548125000000000
    Time = 42651.431548125000000000
    TabOrder = 5
  end
  object dtpWLTime: TDateTimePicker
    Left = 240
    Top = 136
    Width = 73
    Height = 21
    Date = 42651.431548125000000000
    Time = 42651.431548125000000000
    Kind = dtkTime
    TabOrder = 6
  end
end
