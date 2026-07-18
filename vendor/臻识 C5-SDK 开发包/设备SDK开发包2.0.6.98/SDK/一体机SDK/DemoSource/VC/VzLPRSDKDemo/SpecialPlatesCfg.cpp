// SpecialPlatesCfg.cpp : implementation file
//

#include "stdafx.h"
#include "VzLPRSDKDemo.h"
#include "SpecialPlatesCfg.h"
#include "VzLPRClientSDK.h"
#include <VzLPRFaceClientSDK.h>

// CSpecialPlatesCfg dialog

IMPLEMENT_DYNAMIC(CSpecialPlatesCfg, CDialog)

CSpecialPlatesCfg::CSpecialPlatesCfg(CWnd* pParent /*=NULL*/)
	: CDlgOutputAndInBase()
{

}

CSpecialPlatesCfg::~CSpecialPlatesCfg()
{
}

void CSpecialPlatesCfg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
}


BEGIN_MESSAGE_MAP(CSpecialPlatesCfg, CDialog)
	ON_BN_CLICKED(IDOK, &CSpecialPlatesCfg::OnBnClickedOk)
END_MESSAGE_MAP()


// CSpecialPlatesCfg message handlers
void CSpecialPlatesCfg::refresh()
{
	char szBuffer[64] = {0};
	int nRet = VzLPRClient_GetSpecialPlatesCfg(m_hLPRClient,szBuffer,64);
	if (nRet != SUCCESS)
	{
		return;
		UpdateData(false);
	}
	else
	{
		((CButton *)GetDlgItem(IDC_CHECK_PLATE1))->SetCheck(szBuffer[0]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE2))->SetCheck(szBuffer[1]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE3))->SetCheck(szBuffer[2]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE4))->SetCheck(szBuffer[3]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE5))->SetCheck(szBuffer[4]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE6))->SetCheck(szBuffer[5]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE7))->SetCheck(szBuffer[6]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE8))->SetCheck(szBuffer[7]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE9))->SetCheck(szBuffer[8]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE10))->SetCheck(szBuffer[9]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE11))->SetCheck(szBuffer[10]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE12))->SetCheck(szBuffer[11]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE13))->SetCheck(szBuffer[12]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE14))->SetCheck(szBuffer[13]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE15))->SetCheck(szBuffer[14]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE16))->SetCheck(szBuffer[15]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE17))->SetCheck(szBuffer[16]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE18))->SetCheck(szBuffer[17]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE19))->SetCheck(szBuffer[18]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE20))->SetCheck(szBuffer[19]   == 1 ? 1:0);
		// 中间少了一个新能源黄牌 不显示
		((CButton *)GetDlgItem(IDC_CHECK_PLATE22))->SetCheck(szBuffer[21]   == 1 ? 1:0);
		((CButton *)GetDlgItem(IDC_CHECK_PLATE23))->SetCheck(szBuffer[22]   == 1 ? 1:0);
	}
}

void CSpecialPlatesCfg::OnBnClickedOk()
{
	UpdateData(true);
	char szSpePlateCfg[23] = {0};

	szSpePlateCfg[0] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE1))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[1] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE2))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[2] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE3))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[3] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE4))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[4] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE5))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[5] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE6))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[6] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE7))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[7] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE8))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[8] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE9))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[9] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE10))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[10] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE11))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[11] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE12))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[12] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE13))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[13] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE14))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[14] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE15))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[15] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE16))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[16] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE17))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[17] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE18))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[18] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE19))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[19] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE20))->GetCheck() == BST_CHECKED ? 1 : 0;

	// 中间少了一个新能源黄牌 不显示
	szSpePlateCfg[21] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE22))->GetCheck() == BST_CHECKED ? 1 : 0;
	szSpePlateCfg[22] = ((CButton *)GetDlgItem(IDC_CHECK_PLATE23))->GetCheck() == BST_CHECKED ? 1 : 0;


	int ret = VzLPRClient_SetSpecialPlatesCfg(m_hLPRClient,szSpePlateCfg,23);
	if (ret == 0)
	
		MessageBox("特殊车牌设置成功!");
	else
		MessageBox("特殊车牌设置失败!");


}