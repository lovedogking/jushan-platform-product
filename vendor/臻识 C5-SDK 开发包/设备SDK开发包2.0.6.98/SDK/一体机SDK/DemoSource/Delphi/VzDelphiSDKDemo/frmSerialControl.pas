unit frmSerialControl;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, StdCtrls, VzLPRSDK;

const
  MAX_SERIAL_RECV_SIZE = 256;

type
  TFormSerialControl = class(TForm)
    lbl1: TLabel;
    cbbSerialPort: TComboBox;
    btnSerialStart: TButton;
    btnSerialStop: TButton;
    edtSerialRecv: TEdit;
    chkBoxHex: TCheckBox;
    btnClean: TButton;
    edtSerialSend: TEdit;
    chkSendHex: TCheckBox;
    btnSerialSend: TButton;
    procedure SetLPRHandle(mHandle: Integer);
    procedure btnSerialStartClick(Sender: TObject);
    procedure btnSerialStopClick(Sender: TObject);
    procedure btnSerialSendClick(Sender: TObject);
    procedure btnCleanClick(Sender: TObject);
    procedure iUpdateSerialRecvInfo;
  private
    procedure SendHexSerial;
    procedure SendTextSerial;
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormSerialControl: TFormSerialControl;
  m_hLPRClient, nSerialHandle,m_uSizeSerialRecv:Integer;
  bufHexSend : array[0..1023] of Byte;
  m_bufSerialRecv : array[0..256] of Byte;

implementation

{$R *.dfm}

//获取打开设备句柄以及初始化
procedure TFormSerialControl.SetLPRHandle(mHandle : Integer);
begin
  m_hLPRClient := mHandle;
  if(m_hLPRClient > 0) then
  begin
    cbbSerialPort.Items.Add('485_1');
    cbbSerialPort.Items.Add('485_2');
    cbbSerialPort.ItemIndex := 0;
  end;
end;

//更新串口接收数据
procedure  TFormSerialControl.iUpdateSerialRecvInfo();
var
  ShowSize, uSizeRecv, i : Integer;
  strShow : array of Byte;
  bufRecv : array of Byte;
  strRecv : string ;
begin
  ShowSize := MAX_SERIAL_RECV_SIZE * 3 + 1;
  uSizeRecv := m_uSizeSerialRecv;
  
  SetLength(strShow, ShowSize);
  SetLength(bufRecv, MAX_SERIAL_RECV_SIZE);
  CopyMemory(@bufRecv[0], @m_bufSerialRecv[0], uSizeRecv);

  //以十六进制显示
  if(FormSerialControl.chkBoxHex.Checked) then
  begin
    for i := 0 to uSizeRecv do
    begin
      strRecv := '$' + StrPas(@bufRecv[0]);
      CopyMemory(@strShow[0+ 3*i] , PChar(strRecv), ShowSize -i*3 );
    end;
  end
  else  //以十进制显示
  begin
    CopyMemory(@strShow[0], @bufRecv[0], uSizeRecv);
  end;

  FormSerialControl.edtSerialRecv.Text := StrPas(@strShow[0]);  //显示接收的数据
end;

//接收透明通道数据结果回调
function OnSerialRecvDataCallback(nSerialHandle:Integer; pRecvData:Pointer; uRecvSize:Integer;  pUserData:Pointer) : Integer; stdcall;
var
  ret: Integer;
  recv: string;
begin
  if ((uRecvSize > 0)  and (nSerialHandle > 0) and (pRecvData <> nil)) then
  begin
    recv := PChar(pRecvData);
    if (uRecvSize > MAX_SERIAL_RECV_SIZE) then
    begin
      uRecvSize := MAX_SERIAL_RECV_SIZE;
    end;

    if ((m_uSizeSerialRecv + uRecvSize) > MAX_SERIAL_RECV_SIZE ) then
    begin
      m_uSizeSerialRecv := 0;
    end;

    CopyMemory(@m_bufSerialRecv[0], PChar(pRecvData), uRecvSize);

    m_uSizeSerialRecv := m_uSizeSerialRecv + uRecvSize;

    //显示接收的数据
    FormSerialControl.iUpdateSerialRecvInfo();
  end;
end;

//开始
procedure TFormSerialControl.btnSerialStartClick(Sender: TObject);
var
  nPort: Integer;
begin
  if (m_hLPRClient > 0) then
  begin
    nPort := cbbSerialPort.ItemIndex;

    nSerialHandle := VzLPRClient_SerialStart(m_hLPRClient, nPort, OnSerialRecvDataCallback, Self); //开启透明通道
    if(nSerialHandle = 0) then
    begin
      ShowMessage('打开串口失败，请调试!');
    end;
  end;
end;

//停止
procedure TFormSerialControl.btnSerialStopClick(Sender: TObject);
var
  mRet : Integer;
begin
  mRet := VzLPRClient_SerialStop(nSerialHandle); //透明通道停止发送数据
  if(mRet = 0) then
  begin
    nSerialHandle := 0;
  end;
end;

//以十六进制发送
procedure TFormSerialControl.SendHexSerial();
var
  strHex : array[0..2] of Char;
  strSend: array of Char;
  sTextContext, sHexContext : string ;
  uLenstr,i, uSizeData, uSendPoint : Integer;
  uHexContext : Integer;
  mRet : Integer;
begin
  uSizeData := 0;
  uSendPoint := 0;

  sTextContext := edtSerialSend.Text;
  uLenstr := Length(sTextContext);
  SetLength(strSend, uLenstr + 1);
  CopyMemory(@strSend[0], PChar(sTextContext),uLenstr);

  uLenstr := (Length(sTextContext)+ 1) div 3;
  for i := 0 to uLenstr -1 do
  begin
    if((strSend[uSendPoint + 2] <>' ') and (strSend[uSendPoint + 2] <> #0)) then
    begin
      ShowMessage('16进制数据输入格式不正确，参考(00 01 02 F1 F3... )');
      Exit;
    end;

    StrLCopy(@strHex[0], @strSend[uSendPoint + 0], 2);
    uHexContext := StrToInt('$' + StrPas(@strHex[0]));//输入文本内容以十六进制
    bufHexSend[uSizeData] := uHexContext;
    Inc(uSizeData);
    Inc(uSendPoint , 3);
  end ;

  mRet := VzLPRClient_SerialSend(nSerialHandle, @bufHexSend[0], uSizeData);//透明通道发送数据
  if (mRet = 0) then
  begin
     ShowMessage('发送成功!');
  end;
end;

//以十进制发送
procedure TFormSerialControl.SendTextSerial();
var
  sTextContent : string;
  mRet : Integer;
begin
  sTextContent := edtSerialSend.Text ;
  mRet := VzLPRClient_SerialSend(nSerialHandle, PChar(sTextContent), Length(sTextContent)) ;
  if (mRet = 0) then
  begin
     ShowMessage('发送成功!');
  end;

end;

//发送透明通道数据
procedure TFormSerialControl.btnSerialSendClick(Sender: TObject);
begin
  if(nSerialHandle <> 0) then
  begin
    if(chkSendHex.Checked) then
    begin
      SendHexSerial();   //以十六进制发送
    end
    else
    begin
      SendTextSerial();  //以十进制发送
    end;
  end
  else
  begin
    ShowMessage('请先打开出口通道!');
  end;
end;

//清空
procedure TFormSerialControl.btnCleanClick(Sender: TObject);
begin
  edtSerialRecv.Text := '';
end;

end.
