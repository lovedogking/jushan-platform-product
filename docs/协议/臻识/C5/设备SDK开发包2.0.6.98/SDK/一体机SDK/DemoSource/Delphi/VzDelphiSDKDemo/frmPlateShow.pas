unit frmPlateShow;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  Dialogs, jpeg, ExtCtrls;

type
  TFormPlateShow = class(TForm)
    imgQueryShow: TImage;
  procedure SetPicPath(strPath: string);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  FormPlateShow: TFormPlateShow;

implementation

{$R *.dfm}
procedure TFormPlateShow.SetPicPath(strPath : string);
var
  jpegPlate : TJPEGImage;
begin
  jpegPlate := TJPEGImage.Create;
  jpegPlate.LoadFromFile(strPath);

  imgQueryShow.Picture.Graphic := jpegPlate;

  jpegPlate.Free;

end;

end.
