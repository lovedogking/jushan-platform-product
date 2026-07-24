#include "stdafx.h"
#include "Wave.h"

struct WAVE_FORMAT gWaveFmtPcmC2B16R8K = 
{
	1,
	2,
	8192,
	4*8192,
	4,
	16
};

struct WAVE_FORMAT gWaveFmtPcmC1B16R8K = 
{
	1,
	1,
	8192,
	2*8192,
	2,
	16
};

WaveFile *WaveFileOpen(char *fileName, WAVE_FORMAT *pWaveFormat)
{
	FILE *pFWave = NULL;
	if(!(pFWave = fopen(fileName, "wb")))
	{
		printf("cannot open output file\n");
		return(NULL);
	}
	WaveFile *pWaveFile = new WaveFile;
	pWaveFile->pFile = pFWave;
	pWaveFile->dataSize = 0;

	//RIFF
	RIFF_HEADER riffHead;
	char *riffID = "RIFF";
	memcpy(riffHead.szRiffID, riffID, 4);
	riffHead.dwRiffSize = sizeof(RIFF_HEADER) - 8	//减去包括dwRiffSize在内的之前的大小
						+ sizeof(FMT_BLOCK)	+ 2		//加上added大小
						+ sizeof(FACT_BLOCK) + 4	//加上factData的大小
						+ sizeof(DATA_BLOCK);
	char *riffFmt = "WAVE";
	memcpy(riffHead.szRiffFormat, riffFmt, 4);
	fwrite(&riffHead, sizeof(RIFF_HEADER), 1, pFWave);

	//FMT
	FMT_BLOCK fmtBlock;
	char *fmtID = "fmt ";
	memcpy(fmtBlock.szFmtID, fmtID, 4);
	memcpy(&(fmtBlock.wavFormat), pWaveFormat, sizeof(WAVE_FORMAT));
	fmtBlock.dwFmtSize = sizeof(WAVE_FORMAT) + 2;	//加这个2表示后面会接着added
	char added[2] = {0, 0};
	fwrite(&fmtBlock, sizeof(FMT_BLOCK), 1, pFWave);
	fwrite(added, sizeof(char), 2, pFWave);

	//FACT
	FACT_BLOCK factBlock;
	char *factID = "fact";
	memcpy(factBlock.szFactID, factID, 4);
	factBlock.dwFactSize = 4;
	char factData[4] = {0};
	fwrite(&factBlock, sizeof(FACT_BLOCK), 1, pFWave);
	fwrite(factData, sizeof(char), 4, pFWave);

	//DATA
	DATA_BLOCK dataBlock;
	char *dataID = "data";
	memcpy(dataBlock.szDataID, dataID, 4);
	dataBlock.dwDataSize = 0;
	fwrite(&dataBlock, sizeof(DATA_BLOCK), 1, pFWave);

	return(pWaveFile);
}

WaveFile *WaveFileOpenForRead(char *fileName, WAVE_FORMAT *pWaveFormat)
{
	FILE *pFWave = NULL;
	if(!(pFWave = fopen(fileName, "rb")))
	{
		printf("cannot open output file\n");
		return(NULL);
	}
	WaveFile *pWaveFile = new WaveFile;
	pWaveFile->pFile = pFWave;

	//RIFF
	RIFF_HEADER riffHead;
	fread(&riffHead, sizeof(RIFF_HEADER), 1, pFWave);
	printf("%s: %d %s\n", riffHead.szRiffID, riffHead.dwRiffSize, riffHead.szRiffFormat);


	//FMT
	FMT_BLOCK fmtBlock;
	fread(&fmtBlock, sizeof(FMT_BLOCK), 1, pFWave);
	printf("%s: %ld \n", fmtBlock.szFmtID, fmtBlock.dwFmtSize);
	if(pWaveFormat)
	{
		memcpy(pWaveFormat, &(fmtBlock.wavFormat), sizeof(WAVE_FORMAT));
	}
	if(fmtBlock.dwFmtSize == sizeof(WAVE_FORMAT)+2)
	{
		char added[2];
		fread(added, sizeof(char), 2, pFWave);
	}

	//FACT
	FACT_BLOCK factBlock;
	fread(&factBlock, sizeof(FACT_BLOCK), 1, pFWave);
	printf("%s %ld \n", factBlock.szFactID, factBlock.dwFactSize);
	char *pFactData = new char[factBlock.dwFactSize];
	fread(pFactData, sizeof(char), factBlock.dwFactSize, pFWave);

	DATA_BLOCK dataBlock;
	fread(&dataBlock, sizeof(DATA_BLOCK), 1, pFWave);
	printf("%s %ld \n", dataBlock.szDataID, dataBlock.dwDataSize);

	pWaveFile->dataSize = dataBlock.dwDataSize;

	return(pWaveFile);
}

void WaveFileWrite(WaveFile *pWaveFile, const void *pData, int size)
{
	FILE *pFWave = pWaveFile->pFile;
	pWaveFile->dataSize += size;
	fwrite(pData, sizeof(char), size, pFWave);
}

int WaveFileRead(WaveFile *pWaveFile, void *pData, int size)
{
	FILE *pFWave = pWaveFile->pFile;
	int needReadSize = pWaveFile->dataSize > size ? size : (pWaveFile->dataSize);
	int readSize = fread(pData, sizeof(char), needReadSize, pFWave);
	pWaveFile->dataSize -= readSize;

	return(readSize);
}

void WaveFileClose(WaveFile *pWaveFile)
{
	FILE *pFWave = pWaveFile->pFile;
	int dataSize = pWaveFile->dataSize;

	int riffHead_dwRiffSize = sizeof(RIFF_HEADER) - 8	//减去包括dwRiffSize在内的之前的大小
							+ sizeof(FMT_BLOCK)	+ 2		//加上added大小
							+ sizeof(FACT_BLOCK) + 4	//加上factData的大小
							+ sizeof(DATA_BLOCK) + dataSize;
	fseek(pFWave, 4, SEEK_SET);
	fwrite(&riffHead_dwRiffSize, sizeof(int), 1, pFWave);

	int dataBlock_dwDataSize_offset = sizeof(RIFF_HEADER)
									+ sizeof(FMT_BLOCK) + 2
									+ sizeof(FACT_BLOCK) + 4
									+ 4;
	fseek(pFWave, dataBlock_dwDataSize_offset, SEEK_SET);
	fwrite(&dataSize, sizeof(int), 1, pFWave);

	fclose(pFWave);

	delete pWaveFile;
}

void WaveFileCloseForRead(WaveFile *pWaveFile)
{
	FILE *pFWave = pWaveFile->pFile;

	fclose(pFWave);

	delete pWaveFile;
}