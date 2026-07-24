object Form1: TForm1
  Left = 192
  Top = 114
  Width = 870
  Height = 480
  Caption = 'Form1'
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  PixelsPerInch = 96
  TextHeight = 13
  object VZLPRClientCtrl1: TVZLPRClientCtrl
    Left = 32
    Top = 24
    Width = 505
    Height = 297
    TabOrder = 0
    OnLPRPlateInfoOut = VZLPRClientCtrl1LPRPlateInfoOut
    ControlData = {0000010031340000B21E000000000000}
  end
  object Button1: TButton
    Left = 560
    Top = 32
    Width = 81
    Height = 33
    Caption = #25171#24320
    TabOrder = 1
    OnClick = Button1Click
  end
  object Button2: TButton
    Left = 664
    Top = 32
    Width = 97
    Height = 33
    Caption = #20851#38381
    TabOrder = 2
    OnClick = Button2Click
  end
  object Button3: TButton
    Left = 560
    Top = 80
    Width = 81
    Height = 33
    Caption = #25773#25918
    TabOrder = 3
    OnClick = Button3Click
  end
  object Button4: TButton
    Left = 664
    Top = 80
    Width = 89
    Height = 33
    Caption = #20572#27490
    TabOrder = 4
    OnClick = Button4Click
  end
  object ListBox1: TListBox
    Left = 560
    Top = 136
    Width = 193
    Height = 169
    ItemHeight = 13
    TabOrder = 5
  end
end
