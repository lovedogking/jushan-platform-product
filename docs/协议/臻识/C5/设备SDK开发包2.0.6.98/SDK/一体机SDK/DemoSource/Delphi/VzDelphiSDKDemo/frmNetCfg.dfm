object FormNetCfg: TFormNetCfg
  Left = 192
  Top = 130
  Width = 290
  Height = 251
  Caption = 'IP'#37197#32622
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
    Left = 40
    Top = 40
    Width = 49
    Height = 13
    AutoSize = False
    Caption = 'IP'#22320#22336
    WordWrap = True
  end
  object lbl2: TLabel
    Left = 40
    Top = 80
    Width = 65
    Height = 13
    AutoSize = False
    Caption = #23376#32593#25513#30721
    WordWrap = True
  end
  object lbl3: TLabel
    Left = 40
    Top = 120
    Width = 65
    Height = 13
    AutoSize = False
    Caption = #40664#35748#32593#20851
    WordWrap = True
  end
  object edtIP: TEdit
    Left = 120
    Top = 40
    Width = 105
    Height = 21
    TabOrder = 0
    Text = '192.168.1.101'
  end
  object edtMask: TEdit
    Left = 120
    Top = 80
    Width = 105
    Height = 21
    TabOrder = 1
    Text = '255.255.255.0'
  end
  object edtGateWay: TEdit
    Left = 120
    Top = 120
    Width = 105
    Height = 21
    TabOrder = 2
    Text = '192.168.1.1'
  end
  object btnNetCfgOK: TButton
    Left = 176
    Top = 168
    Width = 57
    Height = 25
    Caption = #30830#23450
    TabOrder = 3
    OnClick = btnNetCfgOKClick
  end
end
