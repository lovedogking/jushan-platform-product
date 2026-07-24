object FormPlateQuery: TFormPlateQuery
  Left = 546
  Top = 318
  Width = 573
  Height = 500
  Caption = #36710#29260#26597#35810
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  Position = poScreenCenter
  OnCreate = FromPlateQuery_Create
  PixelsPerInch = 96
  TextHeight = 13
  object lbl1: TLabel
    Left = 16
    Top = 32
    Width = 57
    Height = 13
    AutoSize = False
    Caption = #24320#22987#26102#38388
    WordWrap = True
  end
  object lbl2: TLabel
    Left = 296
    Top = 32
    Width = 57
    Height = 17
    AutoSize = False
    Caption = #32467#26463#26102#38388
    WordWrap = True
  end
  object lbl3: TLabel
    Left = 16
    Top = 64
    Width = 49
    Height = 17
    AutoSize = False
    Caption = #36710#29260#21495
    WordWrap = True
  end
  object lblPageMsg: TLabel
    Left = 16
    Top = 424
    Width = 129
    Height = 13
    AutoSize = False
    WordWrap = True
  end
  object dtpstarttimehour: TDateTimePicker
    Left = 176
    Top = 32
    Width = 81
    Height = 21
    Date = 42633.755296631940000000
    Time = 42633.755296631940000000
    Kind = dtkTime
    TabOrder = 0
  end
  object dtpendtimeday: TDateTimePicker
    Left = 360
    Top = 32
    Width = 89
    Height = 21
    Date = 42633.756091076390000000
    Time = 42633.756091076390000000
    TabOrder = 1
  end
  object dtpendtimehour: TDateTimePicker
    Left = 456
    Top = 32
    Width = 89
    Height = 21
    Date = 42633.756362743060000000
    Time = 42633.756362743060000000
    Kind = dtkTime
    TabOrder = 2
  end
  object edtPlateQuery: TEdit
    Left = 80
    Top = 64
    Width = 89
    Height = 21
    TabOrder = 3
  end
  object btnQuery: TButton
    Left = 176
    Top = 64
    Width = 65
    Height = 25
    Caption = #26597#35810
    TabOrder = 4
    OnClick = btnQueryClick
  end
  object lvQueryResult: TListView
    Left = 16
    Top = 104
    Width = 529
    Height = 313
    Columns = <>
    TabOrder = 5
    ViewStyle = vsReport
    OnDblClick = QueryResult_DbClick
  end
  object btnprepage: TButton
    Left = 368
    Top = 424
    Width = 65
    Height = 25
    Caption = #19978#19968#39029
    TabOrder = 6
    OnClick = btnprepageClick
  end
  object btnnextpage: TButton
    Left = 472
    Top = 424
    Width = 65
    Height = 25
    Caption = #19979#19968#39029
    TabOrder = 7
    OnClick = btnnextpageClick
  end
  object dtpstarttimeday: TDateTimePicker
    Left = 80
    Top = 32
    Width = 89
    Height = 21
    Date = 42634.732311678240000000
    Time = 42634.732311678240000000
    TabOrder = 8
  end
end
