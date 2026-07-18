// DlgSubVideoCfgR1.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "DlgSubVideoCfgR1.h"


// CDlgSubVideoCfgR1 dialog

IMPLEMENT_DYNAMIC(CDlgSubVideoCfgR1, CDialog)

CDlgSubVideoCfgR1::CDlgSubVideoCfgR1(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgSubVideoCfgR1::IDD, pParent)
{
	m_hLPRClient = NULL;

	m_nMinDataRate = 0;
	m_nMaxDataRate = 0;

	m_nDeviceType = 0;

	memset(&m_encode_prop, 0, sizeof(VZ_LPRC_ENCODE_PROP));
}

CDlgSubVideoCfgR1::~CDlgSubVideoCfgR1()
{
}

void CDlgSubVideoCfgR1::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Control(pDX, IDC_CMB_ENCODE_TYPE, m_cmbEncodeType);
	DDX_Control(pDX, IDC_CMB_FRAME_RATE, m_cmbFrameRate);
	DDX_Control(pDX, IDC_CMB_COMPRESS_MODE, m_cmbCompressMode);
	DDX_Control(pDX, IDC_CMB_IMG_QUALITY, m_cmbImgQuality);
	DDX_Control(pDX, IDC_CMB_FRAME_SIZE, m_cmbFrameSize);
	DDX_Control(pDX, IDC_EDIT_RATEVAL, m_editRateval);
	DDX_Control(pDX, IDC_CMB_STREAM_TYPE, m_cmbStreamType);
}


BEGIN_MESSAGE_MAP(CDlgSubVideoCfgR1, CDialog)
	ON_CBN_SELCHANGE(IDC_CMB_STREAM_TYPE, &CDlgSubVideoCfgR1::OnCbnSelchangeCmbStreamType)
	ON_BN_CLICKED(IDOK, &CDlgSubVideoCfgR1::OnBnClickedOk)
	ON_CBN_SELCHANGE(IDC_CMB_COMPRESS_MODE, &CDlgSubVideoCfgR1::OnCbnSelchangeCmbCompressMode)
END_MESSAGE_MAP()


// CDlgSubVideoCfgR1 message handlers

void CDlgSubVideoCfgR1::Load( )
{
	m_cmbStreamType.ResetContent();

	VzLPRClient_GetCameraConfig(m_hLPRClient, VZ_GET_LPR_DEVICE_TYPE, 0, &m_nDeviceType, sizeof(int));
	if (m_nDeviceType == 1)
	{
		// 先获取当前码流
		VZ_LPRC_R_ENCODE_PARAM encode_param = { 0 };
		VzLPRClient_RGet_Encode_Param(m_hLPRClient, 0, &encode_param);

		int cur_stream = encode_param.default_stream;

		int ret = VzLPRClient_GetCameraConfig(m_hLPRClient, VZ_GET_ENCODE_PROP, 0, &m_encode_prop, sizeof(VZ_LPRC_ENCODE_PROP));
		if (ret == 0)
		{
			for (int i = 0; i < VZ_LPRC_MAX_STREAM_TYPE; i++)
			{
				if (strlen(m_encode_prop.stream_type[i].content) > 0)
				{
					m_cmbStreamType.AddString(m_encode_prop.stream_type[i].content);
				}
			}

			int count = m_cmbStreamType.GetCount();
			if (cur_stream >= 0 && cur_stream < m_cmbStreamType.GetCount())
			{
				m_cmbStreamType.SetCurSel(cur_stream);
				LoadStreamParamRC(cur_stream);
			}
		}
	}
	else
	{
		m_cmbStreamType.AddString("主码流");
		m_cmbStreamType.AddString("子码流");

		m_cmbEncodeType.AddString("H264");
		m_cmbEncodeType.SetCurSel(0);

		CString strRate;
		for( int i = 1; i <= 25; i++ )
		{
			strRate.Format("%d", i);
			m_cmbFrameRate.AddString( strRate );
		}

		m_cmbImgQuality.AddString("最流畅");
		m_cmbImgQuality.AddString("较流畅");
		m_cmbImgQuality.AddString("流畅");
		m_cmbImgQuality.AddString("中等");
		m_cmbImgQuality.AddString("清晰");
		m_cmbImgQuality.AddString("较清晰");
		m_cmbImgQuality.AddString("最清晰");

		VZ_LPRC_R_ENCODE_PARAM encode_param = {0};

		VzLPRClient_RGet_Encode_Param(m_hLPRClient, 0, &encode_param);
		m_cmbStreamType.SetCurSel(encode_param.default_stream);

		LoadStreamParam(encode_param.default_stream);
	}
}

void CDlgSubVideoCfgR1::LoadStreamParamRC(int stream_index)
{
	int modeval = 0;
	int index = 0;

	VZ_LPRC_R_ENCODE_PARAM encode_param = { 0 };
	VzLPRClient_RGet_Encode_Param(m_hLPRClient, stream_index, &encode_param);

	int resolution_cur = encode_param.resolution;

	m_nMinDataRate = m_encode_prop.encode_stream[stream_index].data_rate_min;
	m_nMaxDataRate = m_encode_prop.encode_stream[stream_index].data_rate_max;

	m_cmbFrameSize.ResetContent();
	while (m_encode_prop.encode_stream[stream_index].resolution[index].resolution_type > 0)
	{
		m_cmbFrameSize.AddString(m_encode_prop.encode_stream[stream_index].resolution[index].resolution_content);
		m_cmbFrameSize.SetItemData(index,m_encode_prop.encode_stream[stream_index].resolution[index].resolution_type);
		if (resolution_cur == m_encode_prop.encode_stream[stream_index].resolution[index].resolution_type)
		{
			m_cmbFrameSize.SetCurSel(index);
		}

		index++;
	}

	index = 0;
	m_cmbFrameRate.ResetContent();
	for (int i = m_encode_prop.encode_stream[stream_index].frame_rate_min; i <= m_encode_prop.encode_stream[stream_index].frame_rate_max; i++)
	{
		char szRate[16] = {0};
		sprintf(szRate, "%d", i);
		m_cmbFrameRate.AddString(szRate);

	}

	if (encode_param.frame_rate >= m_encode_prop.encode_stream[stream_index].frame_rate_min && encode_param.frame_rate <= m_encode_prop.encode_stream[stream_index].frame_rate_max)
	{
		m_cmbFrameRate.SetCurSel(encode_param.frame_rate - 1);
	}


	m_cmbImgQuality.ResetContent();
	index = 0;
	while (strlen(m_encode_prop.encode_stream[stream_index].video_quality[index].video_quality_content) > 0)
	{
		m_cmbImgQuality.AddString(m_encode_prop.encode_stream[stream_index].video_quality[index].video_quality_content);
		m_cmbImgQuality.SetItemData(index, m_encode_prop.encode_stream[stream_index].video_quality[index].video_quality_type);
		if (encode_param.video_quality == m_encode_prop.encode_stream[stream_index].video_quality[index].video_quality_type)
		{
			m_cmbImgQuality.SetCurSel(index);
		}

		index++;
	}

	CString strRateval;
	strRateval.Format("%d", encode_param.data_rate / 1000);
	m_editRateval.SetWindowText(strRateval);

	//码流控制
	modeval = encode_param.rate_type;
	m_cmbCompressMode.SetCurSel(modeval);

	m_editRateval.EnableWindow(modeval == 0 ? TRUE : FALSE);
	m_cmbImgQuality.EnableWindow(modeval == 0 ? FALSE : TRUE);

	m_cmbEncodeType.ResetContent();
	index = 0;
	while (strlen(m_encode_prop.encode_stream[stream_index].encode_type[index].content) > 0)
	{
		m_cmbEncodeType.AddString(m_encode_prop.encode_stream[stream_index].encode_type[index].content);
		m_cmbEncodeType.SetItemData(index, m_encode_prop.encode_stream[stream_index].encode_type[index].encode_type);
		if (encode_param.encode_type == m_encode_prop.encode_stream[stream_index].encode_type[index].encode_type)
		{
			m_cmbEncodeType.SetCurSel(index);
		}

		index++;
	}
}

void CDlgSubVideoCfgR1::LoadStreamParam( int stream_index )
{
	int modeval = 0;

	VZ_LPRC_R_ENCODE_PARAM encode_param = {0};
	VzLPRClient_RGet_Encode_Param(m_hLPRClient, stream_index, &encode_param);

	bool sub_stream = false;

	// 加载子码流参数
	if( stream_index == 1 )
	{
		sub_stream = true;
	}

	int index = 0;

	VZ_LPRC_R_ENCODE_PARAM_PROPERTY param_property = {0};
	VzLPRClient_RGet_Encode_Param_Property(m_hLPRClient, &param_property);

	m_cmbFrameSize.ResetContent( );
	int resolution_cur = encode_param.resolution;

	m_nMinDataRate = param_property.data_rate_min;
	m_nMaxDataRate = param_property.data_rate_max;

	while( param_property.resolution[index].resolution_type > 0 )
	{
		m_cmbFrameSize.AddString(param_property.resolution[index].resolution_content);
		m_cmbFrameSize.SetItemData(index, param_property.resolution[index].resolution_type);

		if( resolution_cur == param_property.resolution[index].resolution_type )
		{
			m_cmbFrameSize.SetCurSel(index);
		}

		index++;

		if( sub_stream && index >= 3 )
		{
			break;
		}
	}

	if( encode_param.frame_rate >= 1 && encode_param.frame_rate <= 25 )
	{
		m_cmbFrameRate.SetCurSel(encode_param.frame_rate - 1);
	}

	if( encode_param.video_quality >=0 && encode_param.video_quality <= 6 )
	{
		m_cmbImgQuality.SetCurSel(encode_param.video_quality);
	}
	else
	{
		m_cmbImgQuality.SetCurSel(3);
	}

	CString strRateval ;
	strRateval.Format("%d", encode_param.data_rate / 1000);
	m_editRateval.SetWindowText( strRateval );

	//码流控制
	modeval = encode_param.rate_type;
	m_cmbCompressMode.SetCurSel(modeval);

	m_editRateval.EnableWindow(modeval == 0?TRUE:FALSE);
	m_cmbImgQuality.EnableWindow(modeval == 0? FALSE:TRUE);	
	
}

BOOL CDlgSubVideoCfgR1::OnInitDialog()
{
	CDialog::OnInitDialog();

	m_cmbCompressMode.AddString("定码流");
	m_cmbCompressMode.AddString("变码流");

	Load();
	return TRUE;
}

void CDlgSubVideoCfgR1::SetLPRHandle( VzLPRClientHandle hLPRClient )
{
	m_hLPRClient = hLPRClient;
}

void CDlgSubVideoCfgR1::OnCbnSelchangeCmbStreamType()
{
	int cur_sel = m_cmbStreamType.GetCurSel( );

	if (m_nDeviceType == 1)
	{
		LoadStreamParamRC(cur_sel);
	}
	else
	{
		LoadStreamParam(cur_sel);
	}
}

// 保存视频参数配置
void CDlgSubVideoCfgR1::OnBnClickedOk()
{
	if (m_nDeviceType == 1)
	{
		SetRCVideoFrame();
	}
	else
	{
		int stream_type = m_cmbStreamType.GetCurSel( );

		VZ_LPRC_R_ENCODE_PARAM encode_param = {0};
		encode_param.default_stream = stream_type;

		int frame_size = m_cmbFrameSize.GetCurSel();
		int frame_type = m_cmbFrameSize.GetItemData(frame_size);
		encode_param.resolution =  frame_type;

		encode_param.frame_rate = m_cmbFrameRate.GetCurSel() + 1;

		encode_param.rate_type = m_cmbCompressMode.GetCurSel();

		encode_param.video_quality = m_cmbImgQuality.GetCurSel();

		CString strRateval;
		m_editRateval.GetWindowText(strRateval);

		int nRate = atoi(strRateval.GetBuffer(0)) * 1000;

		if( nRate < m_nMinDataRate || nRate > m_nMaxDataRate )
		{
			CString msgInfo;
			msgInfo.Format("码流范围为%d-%d，请重新输入！", m_nMinDataRate / 1000, m_nMaxDataRate / 1000);

			MessageBox(msgInfo);
			return;
		}

		encode_param.data_rate = nRate;

		int ret = VzLPRClient_RSet_Encode_Param(m_hLPRClient, stream_type, &encode_param);
		if( ret != 0 )
		{
			MessageBox("设置视频参数失败，请重试！");
		}

		MessageBox("设置视频参数成功！");
	}
	
}

void CDlgSubVideoCfgR1::OnCbnSelchangeCmbCompressMode()
{
	int nCurSel = m_cmbCompressMode.GetCurSel();
	if ( nCurSel == 0 )
	{
		m_editRateval.EnableWindow(TRUE);
		m_cmbImgQuality.EnableWindow(FALSE);
	}
	else
	{
		m_editRateval.EnableWindow(FALSE);
		m_cmbImgQuality.EnableWindow(TRUE);
	}
}


int CDlgSubVideoCfgR1::SetRCVideoFrame()
{
	int stream_type = m_cmbStreamType.GetCurSel();

	VZ_LPRC_R_ENCODE_PARAM encode_param = { 0 };
	encode_param.default_stream = stream_type;

	int frame_size = m_cmbFrameSize.GetCurSel();
	int frame_type = (int)m_cmbFrameSize.GetItemData(frame_size);
	encode_param.resolution = frame_type;

	encode_param.frame_rate = m_cmbFrameRate.GetCurSel() + 1;

	encode_param.rate_type = m_cmbCompressMode.GetCurSel();

	encode_param.video_quality = m_cmbImgQuality.GetCurSel();

	int encode_cur_sel = m_cmbEncodeType.GetCurSel();
	int nEncodeType = (int)m_cmbEncodeType.GetItemData(encode_cur_sel);
	encode_param.encode_type = nEncodeType;

	CString strRateval;
	m_editRateval.GetWindowText(strRateval);

	int nRate = atoi(strRateval.GetBuffer(0)) * 1000;

	if (nRate < m_nMinDataRate || nRate > m_nMaxDataRate)
	{
		CString msgInfo;
		msgInfo.Format("码流范围为%d-%d，请重新输入！", m_nMinDataRate / 1000, m_nMaxDataRate / 1000);

		MessageBox(msgInfo);
		return -1;
	}

	encode_param.data_rate = nRate;

	int ret = VzLPRClient_RSet_Encode_Param(m_hLPRClient, stream_type, &encode_param);
	if (ret != 0)
	{
		MessageBox("设置视频参数失败，请重试！");
		return -1;
	}

	MessageBox("设置视频参数成功！");

	return 0;
}