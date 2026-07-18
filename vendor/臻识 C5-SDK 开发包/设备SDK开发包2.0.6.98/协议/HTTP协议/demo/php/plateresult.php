<?php
$doc = file_get_contents("php://input");

$fp = fopen("snap_shot.txt", "w");
if(!$fp){
	return;
}
$flag=fwrite($fp, $doc); 
//$flag=fwrite($fp, $jsondecode['AlarmInfoPlate']['result']['PlateResult']['imagePath']); 
if(!$flag) 
{ 
	fclose($fp);
	return;
} 
fclose($fp);

$jsondecode = json_decode($doc,true);

if($jsondecode == null){
	return;
}

// 小图片
if(isset($jsondecode['AlarmInfoPlate']['result']['PlateResult']['imageFragmentFile']))
{
  $small_image = $jsondecode['AlarmInfoPlate']['result']['PlateResult']['imageFragmentFile'];
  if( $small_image != null){
	  $fs_image = fopen("smallimage.jpg", "w");
	  if(!$fs_image){
		  return;
	  }
	  $simage_decoded = base64_decode($small_image);
	  $flag2=fwrite($fs_image, $simage_decoded); 
	  fclose($fs_image);
  }
}

// 大图片
if(isset($jsondecode['AlarmInfoPlate']['result']['PlateResult']['imageFile']))
{
  $image = $jsondecode['AlarmInfoPlate']['result']['PlateResult']['imageFile'];
  if( $image != null){
	  $fp_image = fopen("image.jpg", "w");
	  if(!$fp_image){
		  return;
	  }
	  $image_decoded = base64_decode($image);
	  $flag=fwrite($fp_image, $image_decoded); 
	  fclose($fp_image);
  }
}

$license = $jsondecode['AlarmInfoPlate']['result']['PlateResult']['license'];
$fp_license  = fopen("license.txt", "w");
if($fp_license  )
{ 
	$flag=fwrite($fp_license  , $license ); 
	fclose($fp_license);
}

// 发送开闸命令
echo '{"Response_AlarmInfoPlate":{"info":"ok","content":"...","is_pay":"true"}}';

?>