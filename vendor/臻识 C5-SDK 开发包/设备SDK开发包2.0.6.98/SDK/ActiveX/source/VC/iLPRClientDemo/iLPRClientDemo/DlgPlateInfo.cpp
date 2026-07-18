// DlgPlateInfo.cpp : implementation file
//

#include "stdafx.h"
#include "VZLPRClientDemo.h"
#include "DlgPlateInfo.h"


// CDlgPlateInfo dialog

IMPLEMENT_DYNAMIC(CDlgPlateInfo, CDialog)

CDlgPlateInfo::CDlgPlateInfo(CWnd* pParent /*=NULL*/)
	: CDialog(CDlgPlateInfo::IDD, pParent)
	, m_strPlate(_T(""))
	, m_bEnable(FALSE)
	, m_dtDate(COleDateTime::GetCurrentTime())
	, m_bAlarm(FALSE)
	, m_strComment(_T(""))
{

}

CDlgPlateInfo::~CDlgPlateInfo()
{
}

void CDlgPlateInfo::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	DDX_Text(pDX, IDC_EDIT1, m_strPlate);
	DDX_Check(pDX, IDC_CHK_ENABLE, m_bEnable);
	DDX_DateTimeCtrl(pDX, IDC_DT_OVER, m_dtDate);
	DDX_Check(pDX, IDC_CHK_ALARM, m_bAlarm);
	DDX_Text(pDX, IDC_EDIT_COMMENT, m_strComment);
}


BEGIN_MESSAGE_MAP(CDlgPlateInfo, CDialog)
	ON_BN_CLICKED(IDOK, &CDlgPlateInfo::OnBnClickedOk)
	ON_EN_CHANGE(IDC_EDIT1, &CDlgPlateInfo::OnEnChangeEdit1)
END_MESSAGE_MAP()


// CDlgPlateInfo message handlers
void CDlgPlateInfo::SetPlateInfo( CString strPlate, BOOL bEnable, COleDateTime dt, BOOL bAlarm, CString strComment )
{
	m_strPlate	 = strPlate;
	m_bEnable	 = bEnable;
	m_dtDate	 = dt;
	m_bAlarm	 = bAlarm;
	m_strComment = strComment;
}

void CDlgPlateInfo::GetPlateInfo( CString &strPlate, BOOL &bEnable, COleDateTime &dt, BOOL &bAlarm, CString &strComment )
{
	strPlate = m_strPlate;
	bEnable  = m_bEnable;
	dt		 = m_dtDate;
	bAlarm	 = m_bAlarm;
	strComment = m_strComment;
}


void CDlgPlateInfo::OnBnClickedOk()
{
	UpdateData(TRUE);

	if( m_strPlate == "" )
	{
		MessageBox("请输入车牌号", "提示", MB_OK );
	}

	OnOK();
}

void CDlgPlateInfo::OnEnChangeEdit1()
{
	// TODO:  If this is a RICHEDIT control, the control will not
	// send this notification unless you override the CDialog::OnInitDialog()
	// function and call CRichEditCtrl().SetEventMask()
	// with the ENM_CHANGE flag ORed into the mask.

	// TODO:  Add your control notification handler code here
}
