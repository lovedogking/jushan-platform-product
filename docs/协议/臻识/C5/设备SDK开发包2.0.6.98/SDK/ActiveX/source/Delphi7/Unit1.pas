unit Unit1;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, OleCtrls, VZLPRClientCtrlLib_TLB, StdCtrls;

type
  TForm1 = class(TForm)
    VZLPRClientCtrl1: TVZLPRClientCtrl;
    Button1: TButton;
    Button2: TButton;
    Button3: TButton;
    Button4: TButton;
    ListBox1: TListBox;
    procedure Button1Click(Sender: TObject);
    procedure Button3Click(Sender: TObject);
    procedure Button2Click(Sender: TObject);
    procedure Button4Click(Sender: TObject);
    procedure VZLPRClientCtrl1LPRPlateInfoOut(ASender: TObject;
      const license, color: WideString; colorIndex, type_, confidence,
      bright, nDirection: Smallint; time: Integer; carBright,
      carColor: Smallint; const ip: WideString; resultType: Smallint);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  Form1: TForm1;
  lprHandle:integer;

implementation

{$R *.dfm}

procedure TForm1.Button1Click(Sender: TObject);
begin
lprHandle:= VZLPRClientCtrl1.VzLPRClientOpen('192.168.1.81', 80, 'admin', 'admin');
end;

procedure TForm1.Button3Click(Sender: TObject);
begin
if  lprHandle > 0 then
  begin
    VZLPRClientCtrl1.VzLPRClientStartPlay(lprHandle, 0);
  end
end;

procedure TForm1.Button2Click(Sender: TObject);
begin
if  lprHandle > 0 then
  begin
    VZLPRClientCtrl1.VzLPRClientClose(lprHandle);
  end
end;

procedure TForm1.Button4Click(Sender: TObject);
begin
   if  lprHandle > 0 then
  begin
    VZLPRClientCtrl1.VzLPRClientStopPlay(0);
  end
end;

procedure TForm1.VZLPRClientCtrl1LPRPlateInfoOut(ASender: TObject;
  const license, color: WideString; colorIndex, type_, confidence, bright,
  nDirection: Smallint; time: Integer; carBright, carColor: Smallint;
  const ip: WideString; resultType: Smallint);
begin
ListBox1.Items.Add(license);
end;

end.
