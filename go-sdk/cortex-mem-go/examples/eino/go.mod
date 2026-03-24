module github.com/abforce/cortex-ce/cortex-mem-go/examples/eino

go 1.22

require (
	github.com/abforce/cortex-ce/cortex-mem-go v0.0.0
)

replace (
	github.com/abforce/cortex-ce/cortex-mem-go => ../..
	github.com/abforce/cortex-ce/cortex-mem-go/eino => ../../eino
	github.com/abforce/cortex-ce/cortex-mem-go/langchaingo => ../../langchaingo
	github.com/abforce/cortex-ce/cortex-mem-go/genkit => ../../genkit
)
