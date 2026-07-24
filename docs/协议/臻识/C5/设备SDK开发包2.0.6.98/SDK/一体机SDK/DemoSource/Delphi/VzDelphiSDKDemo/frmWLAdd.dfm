object FormWLAdd: TFormWLAdd
  Left = 192
  Top = 130
  Width = 318
  Height = 287
  Caption = #28155#21152#30333#21517#21333
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
    Left = 32
    Top = 32
    Width = 49
    Height = 13
    AutoSize = False
    Caption = #36710#29260#21495':'
    WordWrap = True
  end
  object lbl2: TLabel
    Left = 32
    Top = 72
    Width = 73
    Height = 13
    AutoSize = False
    Caption = #26159#21542#21551#29992':'
    WordWrap = True
  end
  object lbl3: TLabel
    Left = 32
    Top = 112
    Width = 65
    Height = 13
    AutoSize = False
    Caption = #36807#26399#26102#38388':'
    WordWrap = True
  end
  object lbl4: TLabel
    Left = 32
    Top = 152
    Width = 65
    Height = 13
    AutoSize = False
    Caption = #26159#21542#25253#35686':'
    WordWrap = True
  end
  object edtWLPlate: TEdit
    Left = 104
    Top = 32
    Width = 105
    Height = 21
    TabOrder = 0
  end
  object chkisenable: TCheckBox
    Left = 104
    Top = 72
    Width = 73
    Height = 17
    Caption = #26159
    TabOrder = 1
  end
  object chkisalarm: TCheckBox
    Left = 104
    Top = 152
    Width = 57
    Height = 17
    Caption = #26159
    TabOrder = 2
  end
  object dtpdatalist: TDateTimePicker
    Left = 104
    Top = 112
    Width = 90
    Height = 21
    Date = 42643.632361203700000000
    Time = 42643.632361203700000000
    TabOrder = 3
  end
  object btnWLAddOK: TButton
    Left = 80
    Top = 192
    Width = 57
    Height = 25
    Caption = #20445#23384
    TabOrder = 4
    OnClick = btnWLAddOKClick
  end
  object btnWLCancel: TButton
    Left = 184
    Top = 192
    Width = 57
    Height = 25
    Caption = #21462#28040
    TabOrder = 5
    OnClick = btnWLCancelClick
  end
  object dtpWLAddtime: TDateTimePicker
    Left = 200
    Top = 112
    Width = 82
    Height = 21
    Date = 42643.664864976850000000
    Time = 42643.664864976850000000
    Kind = dtkTime
    TabOrder = 6
  end
end
