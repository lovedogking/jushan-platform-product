object FormInAndOut: TFormInAndOut
  Left = 1442
  Top = 266
  Width = 828
  Height = 554
  Caption = #36755#20837#36755#20986#37197#32622
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
  object pgcInAndOut: TPageControl
    Left = 8
    Top = 8
    Width = 793
    Height = 497
    ActivePage = tsSerialParam
    TabOrder = 0
    object tsOutputCfg: TTabSheet
      Caption = #36755#20986#37197#32622
      object lbl12: TLabel
        Left = 16
        Top = 72
        Width = 65
        Height = 13
        AutoSize = False
        Caption = #35782#21035#36890#36807
        WordWrap = True
      end
      object lbl13: TLabel
        Left = 16
        Top = 120
        Width = 81
        Height = 13
        AutoSize = False
        Caption = #35782#21035#19981#36890#36807
        WordWrap = True
      end
      object lbl14: TLabel
        Left = 16
        Top = 168
        Width = 81
        Height = 13
        AutoSize = False
        Caption = #35782#21035#26080#36710#29260
        WordWrap = True
      end
      object lbl15: TLabel
        Left = 16
        Top = 216
        Width = 81
        Height = 13
        AutoSize = False
        Caption = #35782#21035#40657#21517#21333
        WordWrap = True
      end
      object lbl16: TLabel
        Left = 16
        Top = 264
        Width = 137
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327'/'#30005#24179#36755#20837'1'
        WordWrap = True
      end
      object lbl17: TLabel
        Left = 16
        Top = 312
        Width = 137
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327'/'#30005#24179#36755#20837'2'
        WordWrap = True
      end
      object lbl18: TLabel
        Left = 16
        Top = 360
        Width = 137
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327'/'#30005#24179#36755#20837'3'
        WordWrap = True
      end
      object lbl19: TLabel
        Left = 128
        Top = 48
        Width = 89
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327#36755#20986'1'
        WordWrap = True
      end
      object lbl20: TLabel
        Left = 216
        Top = 48
        Width = 81
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327#36755#20986'2'
        WordWrap = True
      end
      object lbl21: TLabel
        Left = 304
        Top = 48
        Width = 81
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327#36755#20986'3'
        WordWrap = True
      end
      object lbl22: TLabel
        Left = 392
        Top = 48
        Width = 89
        Height = 13
        AutoSize = False
        Caption = #24320#20851#37327#36755#20986'4'
        WordWrap = True
      end
      object lbl23: TLabel
        Left = 480
        Top = 48
        Width = 73
        Height = 13
        AutoSize = False
        Caption = #30005#24179#36755#20986'1'
        WordWrap = True
      end
      object lbl24: TLabel
        Left = 552
        Top = 48
        Width = 73
        Height = 13
        AutoSize = False
        Caption = #30005#24179#36755#20986'2'
        WordWrap = True
      end
      object lbl25: TLabel
        Left = 632
        Top = 48
        Width = 49
        Height = 13
        AutoSize = False
        Caption = 'RS485-1'
        WordWrap = True
      end
      object lbl26: TLabel
        Left = 704
        Top = 48
        Width = 49
        Height = 17
        AutoSize = False
        Caption = 'RS485-2'
        WordWrap = True
      end
      object chk1: TCheckBox
        Left = 160
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 0
        OnClick = chk1Click
      end
      object chk2: TCheckBox
        Left = 240
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 1
        OnClick = chk2Click
      end
      object chk3: TCheckBox
        Left = 320
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 2
        OnClick = chk3Click
      end
      object chk4: TCheckBox
        Left = 400
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 3
        OnClick = chk4Click
      end
      object chk5: TCheckBox
        Left = 480
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 4
        OnClick = chk5Click
      end
      object chk6: TCheckBox
        Left = 560
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 5
        OnClick = chk6Click
      end
      object chk7: TCheckBox
        Left = 640
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 6
        OnClick = chk7Click
      end
      object chk8: TCheckBox
        Left = 720
        Top = 72
        Width = 41
        Height = 17
        TabOrder = 7
        OnClick = chk8Click
      end
      object chk9: TCheckBox
        Left = 720
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 8
        OnClick = chk9Click
      end
      object chk10: TCheckBox
        Left = 640
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 9
        OnClick = chk10Click
      end
      object chk11: TCheckBox
        Left = 560
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 10
        OnClick = chk11Click
      end
      object chk12: TCheckBox
        Left = 480
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 11
        OnClick = chk12Click
      end
      object chk13: TCheckBox
        Left = 400
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 12
        OnClick = chk13Click
      end
      object chk14: TCheckBox
        Left = 320
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 13
        OnClick = chk14Click
      end
      object chk15: TCheckBox
        Left = 240
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 14
        OnClick = chk15Click
      end
      object chk16: TCheckBox
        Left = 160
        Top = 120
        Width = 41
        Height = 17
        TabOrder = 15
        OnClick = chk16Click
      end
      object chk17: TCheckBox
        Left = 720
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 16
        OnClick = chk17Click
      end
      object chk18: TCheckBox
        Left = 640
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 17
        OnClick = chk18Click
      end
      object chk19: TCheckBox
        Left = 560
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 18
        OnClick = chk19Click
      end
      object chk20: TCheckBox
        Left = 480
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 19
        OnClick = chk20Click
      end
      object chk21: TCheckBox
        Left = 400
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 20
        OnClick = chk21Click
      end
      object chk22: TCheckBox
        Left = 320
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 21
        OnClick = chk22Click
      end
      object chk23: TCheckBox
        Left = 240
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 22
        OnClick = chk23Click
      end
      object chk24: TCheckBox
        Left = 160
        Top = 168
        Width = 41
        Height = 17
        TabOrder = 23
        OnClick = chk24Click
      end
      object chk25: TCheckBox
        Left = 720
        Top = 216
        Width = 41
        Height = 17
        TabOrder = 24
        OnClick = chk25Click
      end
      object chk26: TCheckBox
        Left = 640
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 25
        OnClick = chk26Click
      end
      object chk27: TCheckBox
        Left = 560
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 26
        OnClick = chk27Click
      end
      object chk28: TCheckBox
        Left = 480
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 27
        OnClick = chk28Click
      end
      object chk29: TCheckBox
        Left = 400
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 28
        OnClick = chk29Click
      end
      object chk30: TCheckBox
        Left = 320
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 29
        OnClick = chk30Click
      end
      object chk31: TCheckBox
        Left = 240
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 30
        OnClick = chk31Click
      end
      object chk32: TCheckBox
        Left = 160
        Top = 216
        Width = 33
        Height = 17
        TabOrder = 31
        OnClick = chk32Click
      end
      object chk33: TCheckBox
        Left = 720
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 32
        Visible = False
      end
      object chk34: TCheckBox
        Left = 640
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 33
        Visible = False
      end
      object chk35: TCheckBox
        Left = 560
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 34
        OnClick = chk35Click
      end
      object chk36: TCheckBox
        Left = 480
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 35
        OnClick = chk36Click
      end
      object chk37: TCheckBox
        Left = 400
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 36
        OnClick = chk37Click
      end
      object chk38: TCheckBox
        Left = 320
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 37
        OnClick = chk38Click
      end
      object chk39: TCheckBox
        Left = 240
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 38
        OnClick = chk39Click
      end
      object chk40: TCheckBox
        Left = 160
        Top = 264
        Width = 41
        Height = 17
        TabOrder = 39
        OnClick = chk40Click
      end
      object chk41: TCheckBox
        Left = 720
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 40
        Visible = False
      end
      object chk42: TCheckBox
        Left = 640
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 41
        Visible = False
      end
      object chk43: TCheckBox
        Left = 560
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 42
        OnClick = chk43Click
      end
      object chk44: TCheckBox
        Left = 480
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 43
        OnClick = chk44Click
      end
      object chk45: TCheckBox
        Left = 400
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 44
        OnClick = chk45Click
      end
      object chk46: TCheckBox
        Left = 320
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 45
        OnClick = chk46Click
      end
      object chk47: TCheckBox
        Left = 240
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 46
        OnClick = chk47Click
      end
      object chk48: TCheckBox
        Left = 160
        Top = 312
        Width = 41
        Height = 17
        TabOrder = 47
        OnClick = chk48Click
      end
      object chk49: TCheckBox
        Left = 720
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 48
        Visible = False
      end
      object chk50: TCheckBox
        Left = 640
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 49
        Visible = False
      end
      object chk51: TCheckBox
        Left = 560
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 50
        OnClick = chk51Click
      end
      object chk52: TCheckBox
        Left = 480
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 51
        OnClick = chk52Click
      end
      object chk53: TCheckBox
        Left = 400
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 52
        OnClick = chk53Click
      end
      object chk54: TCheckBox
        Left = 320
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 53
        OnClick = chk54Click
      end
      object chk55: TCheckBox
        Left = 240
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 54
        OnClick = chk55Click
      end
      object chk56: TCheckBox
        Left = 160
        Top = 360
        Width = 41
        Height = 17
        TabOrder = 55
        OnClick = chk56Click
      end
      object btnOutpputCfg: TButton
        Left = 344
        Top = 408
        Width = 75
        Height = 25
        Caption = #35774#32622
        TabOrder = 56
        OnClick = btnOutpputCfgClick
      end
    end
    object tsTriggerDelay: TTabSheet
      Caption = #36710#29260#35782#21035#35302#21457#24310#36831
      ImageIndex = 1
      object lbl1: TLabel
        Left = 232
        Top = 192
        Width = 65
        Height = 13
        AutoSize = False
        Caption = #35302#21457#24310#36831':'
        WordWrap = True
      end
      object lbl2: TLabel
        Left = 448
        Top = 192
        Width = 41
        Height = 13
        AutoSize = False
        Caption = #27627#31186
        WordWrap = True
      end
      object lbl3: TLabel
        Left = 176
        Top = 248
        Width = 457
        Height = 13
        AutoSize = False
        Caption = #35828#26126#65306' '#20165#24310#36831#22806#37096#35302#21457#26102#30340#36710#29260#35782#21035#32467#26524#36755#20986#26102#38388#65292#20986#21457#26102#38388#33539#22260'[9,9999]'
        WordWrap = True
      end
      object edtTriggerDelay: TEdit
        Left = 328
        Top = 192
        Width = 89
        Height = 21
        TabOrder = 0
      end
      object btnTriggerDelay: TButton
        Left = 344
        Top = 312
        Width = 75
        Height = 25
        Caption = #35774#32622
        TabOrder = 1
        OnClick = btnTriggerDelayClick
      end
    end
    object tsWhitelistCfg: TTabSheet
      Caption = #30333#21517#21333
      ImageIndex = 2
      object grp1: TGroupBox
        Left = 16
        Top = 32
        Width = 745
        Height = 153
        Caption = #30333#21517#21333#39564#35777
        TabOrder = 0
        object lbl4: TLabel
          Left = 24
          Top = 32
          Width = 105
          Height = 13
          AutoSize = False
          Caption = #30333#21517#21333#21551#29992#26465#20214':'
          WordWrap = True
        end
        object rbOffineAuto: TRadioButton
          Left = 24
          Top = 72
          Width = 113
          Height = 17
          Caption = #33073#26426#33258#21160#21551#29992
          TabOrder = 0
        end
        object rbWhiteStart: TRadioButton
          Left = 216
          Top = 72
          Width = 65
          Height = 17
          Caption = #21551#29992
          TabOrder = 1
        end
        object rbWhiteStop: TRadioButton
          Left = 352
          Top = 72
          Width = 65
          Height = 17
          Caption = #19981#21551#29992
          TabOrder = 2
        end
        object btnWhiteVerify: TButton
          Left = 24
          Top = 112
          Width = 75
          Height = 25
          Caption = #35774#32622
          TabOrder = 3
          OnClick = btnWhiteVerifyClick
        end
      end
      object grp2: TGroupBox
        Left = 16
        Top = 208
        Width = 745
        Height = 257
        Caption = #30333#21517#21333#27169#31946#21305#37197
        TabOrder = 1
        object lbl5: TLabel
          Left = 24
          Top = 32
          Width = 113
          Height = 13
          AutoSize = False
          Caption = #27169#31946#26597#35810#26041#24335#65306
          WordWrap = True
        end
        object lbl6: TLabel
          Left = 240
          Top = 144
          Width = 105
          Height = 13
          AutoSize = False
          Caption = #20801#35768#35823#35782#21035#38271#24230':'
          WordWrap = True
        end
        object rbWhiteAllMatch: TRadioButton
          Left = 24
          Top = 64
          Width = 113
          Height = 17
          Caption = #31934#30830#21305#37197
          TabOrder = 0
          OnClick = rbWhiteAllMatch_Clik
        end
        object rbWhitePartMatch: TRadioButton
          Left = 24
          Top = 104
          Width = 241
          Height = 17
          Caption = #30456#20284#23383#31526#21305#37197' '#65288#21253#25324': 0-D, 8-Q, E-F '#65289
          TabOrder = 1
          OnClick = rbWhitePartMatch_Clik
        end
        object rbWhiteFuccyMatch: TRadioButton
          Left = 24
          Top = 144
          Width = 153
          Height = 17
          Caption = #26222#36890#23383#31526#27169#31946#21305#37197
          TabOrder = 2
          OnClick = rbWhiteFuccyMatch_Clik
        end
        object pnl1: TPanel
          Left = 352
          Top = 136
          Width = 233
          Height = 33
          TabOrder = 3
          object rbWhiteLen1: TRadioButton
            Left = 16
            Top = 8
            Width = 49
            Height = 17
            Caption = '1'
            TabOrder = 0
          end
          object rbWhiteLen2: TRadioButton
            Left = 80
            Top = 8
            Width = 49
            Height = 17
            Caption = '2'
            TabOrder = 1
          end
          object rbWhiteLen3: TRadioButton
            Left = 152
            Top = 8
            Width = 33
            Height = 17
            Caption = '3'
            TabOrder = 2
          end
        end
        object chkIgnore: TCheckBox
          Left = 24
          Top = 184
          Width = 97
          Height = 17
          Caption = #24573#30053#27721#23383
          TabOrder = 4
        end
        object btnWhiteMatch: TButton
          Left = 24
          Top = 224
          Width = 75
          Height = 25
          Caption = #35774#32622
          TabOrder = 5
          OnClick = btnWhiteMatchClick
        end
      end
    end
    object tsSerialParam: TTabSheet
      Caption = #20018#21475#21442#25968
      ImageIndex = 3
      object lbl7: TLabel
        Left = 224
        Top = 88
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #20018#21475#21495
        WordWrap = True
      end
      object lbl8: TLabel
        Left = 224
        Top = 136
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #27874#29305#29575
        WordWrap = True
      end
      object lbl9: TLabel
        Left = 224
        Top = 184
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #26657#39564#20301
        WordWrap = True
      end
      object lbl10: TLabel
        Left = 224
        Top = 232
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #20572#27490#20301
        WordWrap = True
      end
      object lbl11: TLabel
        Left = 224
        Top = 280
        Width = 49
        Height = 13
        AutoSize = False
        Caption = #25968#25454#20301
        WordWrap = True
      end
      object cbbSerialPort: TComboBox
        Left = 328
        Top = 88
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 0
        OnChange = cbbSerialPort_Change
      end
      object cbbBound: TComboBox
        Left = 328
        Top = 136
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 1
      end
      object cbbParity: TComboBox
        Left = 328
        Top = 184
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 2
      end
      object cbbStop: TComboBox
        Left = 328
        Top = 232
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 3
      end
      object cbbDataBit: TComboBox
        Left = 328
        Top = 280
        Width = 145
        Height = 21
        ItemHeight = 13
        TabOrder = 4
      end
      object btnSerialParam: TButton
        Left = 392
        Top = 352
        Width = 75
        Height = 25
        Caption = #35774#32622
        TabOrder = 5
        OnClick = btnSerialParamClick
      end
    end
  end
end
